package stpTrackingConsumer.handleLimitEvent;


import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
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
import ru.qa.tinkoff.investTracking.entities.ManagementFee;
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
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.LiteStrategy;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
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
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.*;

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

public class HandleLimitEventTest {
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


    String tickerSBER = "SBER";
    String tradingClearingAccountSBER = "L01+00002F00";
    String classCodeSBER = "TQBR";
    String instrumet1 = tickerSBER + "_" + classCodeSBER;
    String quantitySBER = "20";

    String tickerSU = "SU29009RMFS6";
    String tradingClearingAccountSU = "L01+00002F00";
    String quantitySU = "5";
    String classCodeSU = "TQOB";
    String instrumetSU = tickerSU + "_" + classCodeSU;
    BigDecimal minPriceIncrement = new BigDecimal("0.001");


    String tickerUSD = "USD000UTSTOM";
    String tradingClearingAccountUSD = "MB9885503216";
    String classCodeUSD = "CETS";
    String quantityUSD = "2000";


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

            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }

    @SneakyThrows
    @Test
    @AllureId("1084018")
    @DisplayName("C1084018.HandleLimitEvent.Обработка события от Miof и создание команды по изменению базовой валюты")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1084018() {
        String siebelIdSlave = "1-8VVOWFO";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest " + x;
        String description = "test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = "MMV128813156";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
//        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, quantity1, ticker2, tradingClearingAccount2, quantity2);
        List<MasterPortfolio.Position> positionMasterList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //смотрим версию и базовую валюту в GRPC middle
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
        int versionMiddle = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
        String baseMoney = Double.toString(middleQuantityBaseMoney);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, versionMiddle, 1, baseMoney,
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по валюте в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "RUB", 1);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        //считаем значение BaseMoneyPosition в комманде
        double quantityMoneyCommand = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //сохраняем данные по позиции рубли
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositions.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals("RUB"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //считаем значение BaseMoneyPosition в ответе из miof
        double quantityCurrencyMiof = listMoney.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale());
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("BaseMoneyPosition не равен", quantityMoneyCommand, is(quantityCurrencyMiof));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
        assertThat("Action не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getAction().getAction().name(), is("ADJUST_CURRENCY"));
    }


    @SneakyThrows
    @Test
    @AllureId("1084044")
    @DisplayName("C1084044.HandleLimitEvent.Обработка события от Miof и создание команды по изменению позиции")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1084044() {
        String siebelIdSlave = "1-6J4U1TZ";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest " + x;
        String description = "test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = "UMA676513176";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
        int versionMiddleBefore = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
        String baseMoney = Double.toString(middleQuantityBaseMoney);
        //сохраняем данные по бумаге из ответа miof в список
        List<ru.tinkoff.invest.miof.Client.SecurityPosition> listSecurities = clientPositionsBefore.getResponse().getClientPositions().getSecuritiesList().stream()
            .filter(ls -> ls.getTicker().equals(tickerSU))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //расчитываем значение количество бумаг
        double quantitySecurityMiof = listSecurities.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listSecurities.get(0).getBalance().getScale());
        int valueMaster = (int) quantitySecurityMiof + 1;
        String quantityMaster = Integer.toString(valueMaster);
        String quantitySlave = Double.toString(quantitySecurityMiof);
        //создаем портфель мастера
//        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, quantity1, ticker2, tradingClearingAccount2, quantity2);

        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne( date, tickerSU,  tradingClearingAccountSU, quantityMaster);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
//        //создаем подписку на стратегию
//       steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLight(tickerSU, tradingClearingAccountSU,
            quantitySlave, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddleBefore, 2,
            baseMoney, date, createListSlavePos);
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по бумаге в miof
        steps.getClientAdjustSecurityMiof(clientCodeSlave, contractIdSlave, tickerSU, 1);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        double quantitySecurityCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        List<ru.tinkoff.invest.miof.Client.SecurityPosition> listSecuritiesNew = clientPositions.getResponse().getClientPositions().getSecuritiesList().stream()
            .filter(ls -> ls.getTicker().equals(tickerSU))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        double quantitySecurityMiofNew = listSecuritiesNew.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listSecuritiesNew.get(0).getBalance().getScale());
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(tickerSU));
        assertThat("trading_clearing_account не равен", portfolioCommand.getPortfolio().getPosition(0).getTradingClearingAccount(),
            is(listSecurities.get(0).getAccountId()));
        assertThat("quantity по бумагам  не равен", (quantitySecurityMiofNew), is(quantitySecurityCommand));
        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).getAction().getAction().name(), is("ADJUST_SECURITY"));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1084158")
    @DisplayName("C1084158.HandleLimitEvent.Обработка события от Miof и создание команды по изменению позиции - валюты")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1084158() {
        String siebelIdSlave = "1-36F29PH";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest " + x;
        String description = "test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = "LMA779872317";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
        int versionMiddleBefore = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
        String baseMoney = Double.toString(middleQuantityBaseMoney);
        //сохраняем данные по бумаге из ответа miof в список
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositionsBefore.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals("USD"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //расчитываем значение количество бумаг
        double quantityMiof = listMoney.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale());
        int valueMaster = (int) quantityMiof + 1;
        String quantityMaster = Integer.toString(valueMaster);
        String quantitySlave = Double.toString(quantityMiof);
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne( date, tickerUSD,  tradingClearingAccountUSD, quantityMaster);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLight(tickerUSD, tradingClearingAccountUSD,
            quantitySlave, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddleBefore, 2,
            baseMoney, date, createListSlavePos);
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по бумаге в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "USD", 1);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        String key = message.getKey();
        double quantitySecurityCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //сохраняем данные по позиции рубли
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoneyList = clientPositions.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals("USD"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        double quantityCurrencyMiof = listMoneyList.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoneyList.get(0).getBalance().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(tickerUSD));
        assertThat("trading_clearing_account не равен", portfolioCommand.getPortfolio().getPosition(0).getTradingClearingAccount(),
            is(listMoney.get(0).getAccountId()));
        assertThat("quantity по бумагам  не равен", (quantityCurrencyMiof), is(quantitySecurityCommand));
        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).getAction().getAction().name(), is("ADJUST_CURRENCY"));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("580020")
    @DisplayName("C580020.HandleLimitEvent.У contractId, state != 'tracked'")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C580020() {
        String SIEBEL_ID_SLAVE = "1-D24XMPH";
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
        String clientCodeSlave = "GAV949564656";
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, tickerSBER, tradingClearingAccountSBER, quantitySBER, tickerSU, tradingClearingAccountSU, quantitySU);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.deleteSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по бумаге в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "RUB", 1);
        //вызываем метод middleOffice по изменению позиции клиента по валюте
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


    @SneakyThrows
    @Test
    @AllureId("580024")
    @DisplayName("C580024.HandleLimitEvent.Не найден ticker позиции по ключу = money_limit.currency в настройке money-tickers")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C580024() {
        String SIEBEL_ID_SLAVE = "1-36705ZD";
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
        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, tickerSBER, tradingClearingAccountSBER, quantitySBER, tickerSU, tradingClearingAccountSU, quantitySU);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // вычитываем из топика кафка tracking.slave.command tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //изменяем позицию по валюте в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "GBP", 1);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.contract.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("912915")
    @DisplayName("C912915.HandleLimitEvent.Не найдена стратегия в strategyCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C912915() {
        String SIEBEL_ID_SLAVE = "1-8VVOWFO";
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        strategyId = UUID.randomUUID();
        //смотрим версию и базовую валюту в GRPC middle
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
        int versionMiddle = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
        String baseMoney = Double.toString(middleQuantityBaseMoney);
        //создаем записи в client и contract для slave
        steps.createContract(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId);
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, versionMiddle, 1, baseMoney,
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));
        // вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //изменяем позицию по валюте в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "RUB", 1);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.contract.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
    }

    @SneakyThrows
    @Test
    @AllureId("1034763")
    @DisplayName("C1034763.HandleLimitEvent.[depo_limit|money_limit].open_balance_value + [depo_limit|money_limit].locked_value >= 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1034763() {
        String SIEBEL_ID_SLAVE = "1-EYLV5GT";
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
        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, tickerSBER, tradingClearingAccountSBER, quantitySBER, tickerSU, tradingClearingAccountSU, quantitySU);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
       //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "USD", -1);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event eventBlock =null;
        for (int i = 0; i < messages.size(); i++) {
            Tracking.Event event = Tracking.Event.parseFrom(messages.get(i).getValue());
            if (event.getContract().getBlocked()== true) {
                eventBlock =event;
                }
        }
        log.info("Событие  в tracking.contract.event:  {}", eventBlock);
        //проверяем, данные в сообщении
        assertThat("action события не равен", eventBlock.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (eventBlock.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (eventBlock.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (eventBlock.getContract().getBlocked()), is(true));
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

    List<MasterPortfolio.Position> masterPositionsOne(Date date, String tickerOne, String tradingClearingAccountOne,
                                                   String quantityOne) {
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
