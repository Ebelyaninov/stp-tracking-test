package stpTrackingConsumer.handleLimitEvent;

import com.google.protobuf.InvalidProtocolBufferException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.ManagementFeeDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.trackingConsumerSteps.StpTrackingConsumerSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;

@Slf4j
@Epic("handleLimitEvent Обработка событий об изменении позиций")
@Feature("TAP-11072")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-consumer")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingConsumerSteps.class,
    GrpcServicesAutoConfiguration.class
})

public class HandleLimitEventErrorTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;

    @Autowired
    StringToByteSenderService kafkaSenderString;
    @Autowired
    MiddleGrpcService middleGrpcService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingConsumerSteps steps;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    ManagementFeeDao managementFeeDao;



    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Subscription subscription;
    Contract contract;
    UUID strategyId;
    SlavePortfolio slavePortfolio;
    String siebelIdMaster = "1-51Q76AT";


    String ticker1 = "SBER";
    String tradingClearingAccount1 = "L01+00002F00";
    String classCode1 = "TQBR";
    String instrumet1 = ticker1 + "_" + classCode1;
    String quantity1 = "20";

    String ticker2 = "SU29009RMFS6";
    String tradingClearingAccount2 = "L01+00002F00";
    //    public String tradingClearingAccount2 = "L01+00000F00";
    String quantity2 = "5";
    String classCode2 = "TQOB";
    String instrumet2 = ticker2 + "_" + classCode2;
    BigDecimal minPriceIncrement = new BigDecimal("0.001");


    String ticker3 = "USD000UTSTOM";
    String tradingClearingAccount3 = "MB9885503216";
    String classCode3 = "CETS";
    String quantity3 = "2000";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(steps.clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(steps.strategyMaster);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientMaster);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("580022")
    @DisplayName("C580022.HandleLimitEvent.Параметр version не заполнен")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C580022() {
        String SIEBEL_ID_SLAVE = "1-2ML9VUT";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest " + x;
        String description = "description test стратегия autotest update adjust currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = "PDA403823902";
//        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, quantity1, ticker2, tradingClearingAccount2, quantity2);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        // вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по валюте в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "RUB", 1);
        //смотрим, сообщение, которое поймали в топике kafka tracking.slave.command
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND),
                is(empty())
            ).stream().findFirst();

    }


    //общие методы для тестов
    List<MasterPortfolio.Position> masterPositions(Date date, String tickerOne, String tradingClearingAccountOne,
                                                   String quantityOne, String tickerTwo, String tradingClearingAccountTwo,
                                                   String quantityTwo) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerOne)
            .tradingClearingAccount(tradingClearingAccountOne)
            .quantity(new BigDecimal(quantityOne))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerTwo)
            .tradingClearingAccount(tradingClearingAccountTwo)
            .quantity(new BigDecimal(quantityTwo))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }

    //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
    CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> grpcMiofRequest(String contractIdSlave) {
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReqBefore = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReqBefore);
        return clientPositions;
    }

    //Смотрим, сообщение, которое поймали в топике kafka
    Tracking.PortfolioCommand getMessageFromKafka(Topics topic) throws InvalidProtocolBufferException {
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        return portfolioCommand;
    }

}
