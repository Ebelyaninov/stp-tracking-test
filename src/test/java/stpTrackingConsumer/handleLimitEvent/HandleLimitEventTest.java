package stpTrackingConsumer.handleLimitEvent;


import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import limit.Limit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
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
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.trackingConsumerSteps.StpTrackingConsumerSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("handleLimitEvent Обработка событий об изменении позиций")
@Feature("TAP-11072")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-consumer")
@Tags({@Tag("stp-tracking-consumer"), @Tag("handleLimitEvent")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingConsumerSteps.class,
    GrpcServicesAutoConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingInstrumentConfiguration.class
})

public class HandleLimitEventTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    OldKafkaService oldKafkaService;
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
    @Autowired
    StpInstrument instrument;


    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    List<String> contractIdSlaves = new ArrayList<>();
    Subscription subscription;
    Contract contract;
    UUID strategyId;
    SlavePortfolio slavePortfolio;
    String siebelIdMaster = "1-51Q76AT";

//
//    String tickerSBER = "SBER";
//    String tradingClearingAccountSBER = "L01+00002F00";
//    String classCodeSBER = "TQBR";
//    String instrumet1 = tickerSBER + "_" + classCodeSBER;
    String quantitySBER = "20";
//
//    String ticker = "AAPL";
//    String tradingClearingAccount = "TKCBM_TCAB";
//    String classCode = "SPBXM";
//
//    String tickerSU = "SU29009RMFS6";
//    String tradingClearingAccountSU = "L01+00000F00";
    String quantitySU29009RMFS6 = "5";
//    String classCodeSU = "TQOB";
//    String instrumetSU = tickerSU + "_" + classCodeSU;
    BigDecimal minPriceIncrement = new BigDecimal("0.001");
//
//
//    String tickerUSD = "USDRUB";
//    String tradingClearingAccountUSD = "MB9885503216";
//    String classCodeUSD = "CETS";
    String quantityUSD = "2000";
//
//    String tickerGBP = "GBPRUB";
//    String tradingClearingAccountGBP = "MB9885503216";
//
//    String tickerJPY = "JPYRUB";
//    String tradingClearingAccountJPY = "MB9885503216";
//
//    String tickerRUB = "RUB";
//    String tradingClearingAccountRUB = "MB9885503216";

    String description = "description test стратегия autotest consumer";


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

//

//    @AfterAll
////    void invalidCache() {
////        step("очищаем кеш клиента автоследования", () -> {
////            try {
////                for (int i = 0; i < contractIdSlaves.size(); i++) {
////                    steps.createEventInTrackingEvent(contractIdSlaves.get(i));
////                }
////            } catch (Exception e) {
////            }
////        });
////    }

    @SneakyThrows
    @Test
    @AllureId("1084018")
    @DisplayName("C1084018.HandleLimitEvent.Обработка события от Miof и создание команды по изменению базовой валюты")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1084018() {
        String siebelIdSlave = "1-8VVOWFO";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        contractIdSlaves.add(contractIdSlave);

//        UUID investIdSlave = UUID.fromString("c6b7fffd-554d-4b7c-802e-81e000d6d4c2");
//        contractIdSlave = "2000007043";
        String clientCodeSlave = "MMV128813156";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
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
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, versionMiddle, 1, baseMoney,
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));
        steps.createEventInTrackingEvent(contractIdSlave);
        Thread.sleep(5000);
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

    // нужна бумага в midle SU29009RMFS6
    @SneakyThrows
    @Test
    @AllureId("1084044")
    @DisplayName("C1084044.HandleLimitEvent.Обработка события от Miof и создание команды по изменению позиции")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1084044() {
        String siebelIdSlave = "1-6J4U1TZ";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        UUID investIdSlave = UUID.fromString("8e522d18-b99e-4a25-b4a2-a5acf41d3123");
//        contractIdSlave = "2000017430";
//        contractIdSlaves.add(contractIdSlave);
        String clientCodeSlave = "UMA676513176";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
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
            .filter(ls -> ls.getTicker().equals(instrument.tickerSU29009RMFS6))
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

        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, quantityMaster);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
//        //создаем подписку на стратегию
//       steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            quantitySlave, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddleBefore, 2,
            baseMoney, date, createListSlavePos);
        steps.createEventInTrackingEvent(contractIdSlave);
        Thread.sleep(5000);
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по бумаге в miof
        steps.getClientAdjustSecurityMiof(clientCodeSlave, contractIdSlave, instrument.tickerSU29009RMFS6, 1);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        double quantitySecurityCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        List<ru.tinkoff.invest.miof.Client.SecurityPosition> listSecuritiesNew = clientPositions.getResponse().getClientPositions().getSecuritiesList().stream()
            .filter(ls -> ls.getTicker().equals(instrument.tickerSU29009RMFS6))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        double quantitySecurityMiofNew = listSecuritiesNew.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listSecuritiesNew.get(0).getBalance().getScale());
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(instrument.tickerSU29009RMFS6));
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
    @DisplayName("C1084158.HandleLimitEvent.Обработка события от Miof и создание команды по изменению позиции - валюты USD")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1084158() {
        String siebelIdSlave = "1-36F29PH";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        UUID investIdSlave = UUID.fromString("b4295c47-9364-497c-bcc4-1eee1c462bb6");
//        contractIdSlave = "2000021259";
        contractIdSlaves.add(contractIdSlave);
        String clientCodeSlave = "LMA779872317";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerUSD, instrument.tradingClearingAccountUSD, quantityMaster);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerUSD, instrument.tradingClearingAccountUSD,
            quantitySlave, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddleBefore, 2,
            baseMoney, date, createListSlavePos);
        steps.createEventInTrackingEvent(contractIdSlave);
        Thread.sleep(5000);
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
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(instrument.tickerUSDRUB));
        assertThat("trading_clearing_account не равен", portfolioCommand.getPortfolio().getPosition(0).getTradingClearingAccount(),
            is(listMoney.get(0).getAccountId()));
        assertThat("quantity по бумагам  не равен", (quantityCurrencyMiof), is(quantitySecurityCommand));
        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).getAction().getAction().name(), is("ADJUST_CURRENCY"));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1507329")
    @DisplayName("C1507329.HandleLimitEvent.Обработка события от Miof и создание команды по изменению позиции - валюты GBP")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1507329() {
        String siebelIdSlave = "1-51P8KN5";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        UUID investIdSlave = UUID.fromString("b5ba0624-7a63-4a7f-9668-1d33617815bb");
//        contractIdSlave = "2000056792";
        contractIdSlaves.add(contractIdSlave);
        String clientCodeSlave = "KMV144216105";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
        int versionMiddleBefore = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
        String baseMoney = Double.toString(middleQuantityBaseMoney);
        //сохраняем данные по бумаге из ответа miof в список
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositionsBefore.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals("GBP"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //расчитываем значение количество бумаг
        double quantityMiof = listMoney.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale());
        int valueMaster = (int) quantityMiof + 1;
        String quantityMaster = Integer.toString(valueMaster);
        String quantitySlave = Double.toString(quantityMiof);
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerGBP, instrument.tradingClearingAccountGBP,
            quantitySlave, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddleBefore, 2,
            baseMoney, date, createListSlavePos);
        steps.createEventInTrackingEvent(contractIdSlave);
        Thread.sleep(5000);
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //изменяем позицию по бумаге в miof
        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "GBP", 1);
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
            .filter(ls -> ls.getCurrency().equals("GBP"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        double quantityCurrencyMiof = listMoneyList.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoneyList.get(0).getBalance().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(instrument.tickerGBP));
        assertThat("trading_clearing_account не равен", portfolioCommand.getPortfolio().getPosition(0).getTradingClearingAccount(),
            is(listMoney.get(0).getAccountId()));
        assertThat("quantity по бумагам  не равен", (quantityCurrencyMiof), is(quantitySecurityCommand));
        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).getAction().getAction().name(), is("ADJUST_CURRENCY"));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
    }


//    @SneakyThrows
//    @Test
//    @AllureId("1507393")
//    @DisplayName("C1507393.HandleLimitEvent.Обработка события от Miof и создание команды по изменению позиции - валюты JPY")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
//    void C1084158_222() {
//        String siebelIdSlave = "1-CPTNTIF";
//        Random ran = new Random();
//        int x = ran.nextInt(6) + 5;
//        String title = "autotest " + x;
//        String description = "test стратегия autotest";
//        strategyId = UUID.randomUUID();
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту slave в api сервиса счетов
////        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
////        UUID investIdSlave = resAccountSlave.getInvestId();
////        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        UUID investIdSlave = UUID.fromString("7cc7003c-d6c9-45e7-83ac-b4100231251e");
//        contractIdSlave = "2000057303";
//        contractIdSlaves.add(contractIdSlave);
//        String clientCodeSlave = "GMN591934879";
//        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
//        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
//        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
//        Date date = Date.from(utc.toInstant());
//        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
//        int versionMiddleBefore = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
//        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
//        String baseMoney = Double.toString(middleQuantityBaseMoney);
//        //сохраняем данные по бумаге из ответа miof в список
//        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositionsBefore.getResponse().getClientPositions().getMoneyList().stream()
//            .filter(ls -> ls.getCurrency().equals("JPY"))
//            .filter(ls -> ls.getKind().name().equals("T365"))
//            .collect(Collectors.toList());
//        //расчитываем значение количество бумаг
//        double quantityMiof = listMoney.get(0).getBalance().getUnscaled()
//            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale());
//        int valueMaster = (int) quantityMiof + 1;
//        String quantityMaster = Integer.toString(valueMaster);
//        String quantitySlave = Double.toString(quantityMiof);
//        //создаем портфель мастера
//        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne( date, tickerSBER,  tickerSBER, "10");
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
//        //создаем подписку на стратегию
//        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
//        steps.createSubcription(investIdSlave, null,contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        //создаем список позиций в портфеле slave
//        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLight(tickerJPY, tradingClearingAccountJPY,
//            quantitySlave, date);
//        //создаем запись в кассандре
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddleBefore, 2,
//            baseMoney, date, createListSlavePos);
//        steps.createEventInTrackingEvent(contractIdSlave);
//        Thread.sleep(5000);
//        //вычитываем все события из топика tracking.slave.command
//        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
//        //изменяем позицию по бумаге в miof
//        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "JPY", 1);
//        //Смотрим, сообщение, которое поймали в топике kafka
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
//        Pair<String, byte[]> message = messages.stream()
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
//        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
//        String key = message.getKey();
//        double quantitySecurityCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());
//        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
//        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
//        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
//        //сохраняем данные по позиции рубли
//        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoneyList = clientPositions.getResponse().getClientPositions().getMoneyList().stream()
//            .filter(ls -> ls.getCurrency().equals("JPY"))
//            .filter(ls -> ls.getKind().name().equals("T365"))
//            .collect(Collectors.toList());
//        double quantityCurrencyMiof = listMoneyList.get(0).getBalance().getUnscaled()
//            * Math.pow(10, -1 * listMoneyList.get(0).getBalance().getScale());
//        //проверяем, данные в команде
//        assertThat("key не равен", key, is(contractIdSlave));
//        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
//        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
//        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(tickerJPY));
//        assertThat("trading_clearing_account не равен", portfolioCommand.getPortfolio().getPosition(0).getTradingClearingAccount(),
//            is(listMoney.get(0).getAccountId()));
//        assertThat("quantity по бумагам  не равен", (quantityCurrencyMiof), is(quantitySecurityCommand));
//        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).getAction().getAction().name(), is("ADJUST_CURRENCY"));
//        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
//        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
//    }


    @SneakyThrows
    @Test
    @AllureId("580020")
    @DisplayName("C580020.HandleLimitEvent.У contractId, state != 'tracked'")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C580020() {
        String SIEBEL_ID_SLAVE = "1-CPTNTIF";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();

//        UUID investIdSlave = UUID.fromString("7cc7003c-d6c9-45e7-83ac-b4100231251e");
//        contractIdSlave = "2000057303";
        String clientCodeSlave = "GMN591934879";
        contractIdSlaves.add(contractIdSlave);
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, quantitySBER, instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, quantitySU29009RMFS6);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем удаленную подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now();
        steps.createSubcriptionDelete(investIdSlave, null, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new Timestamp(endSubTime.toInstant().toEpochMilli()), false);
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


//    @SneakyThrows
//    @Test
//    @AllureId("580024")
//    @DisplayName("C580024.HandleLimitEvent.Не найден ticker позиции по ключу = money_limit.currency в настройке money-tickers")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
//    void C580024() {
//        String SIEBEL_ID_SLAVE = "1-36705ZD";
//        Random ran = new Random();
//        int x = ran.nextInt(6) + 5;
//        String title = "autotest " + x;
//        String description = "description test стратегия autotest update adjust currency";
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту slave в api сервиса счетов
////        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
////        UUID investIdSlave = resAccountSlave.getInvestId();
////        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
////         String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
//        UUID investIdSlave = UUID.fromString("fd0df403-65eb-4698-a1b7-8ab49e0e6949");
//        contractIdSlave = "2050447842";
//        String clientCodeSlave ="770014052245";
//            contractIdSlaves.add(contractIdSlave);
//        strategyId = UUID.randomUUID();
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
//        Date date = Date.from(utc.toInstant());
//        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
//        //создаем портфель мастера
//        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, tickerSBER, tradingClearingAccountSBER, quantitySBER, tickerSU, tradingClearingAccountSU, quantitySU);
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
//        //создаем подписку на стратегию
//        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
//        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        // вычитываем из топика кафка tracking.slave.command tracking.contract.event все offset
//        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
//        //изменяем позицию по валюте в miof
//        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "GBP", 1);
//        //Смотрим, сообщение, которое поймали в топике kafka
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
//        Pair<String, byte[]> message = messages.stream()
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
//        log.info("Событие  в tracking.contract.event:  {}", event);
//        //проверяем, данные в сообщении
//        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
//        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
//        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
//        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
//    }


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
//        UUID investIdSlave = UUID.fromString("aa4b562f-1e89-425d-a901-b18ac0615e2f");
//        contractIdSlave = "2000007043";
//        String clientCodeSlave = "MMV128813156";
        contractIdSlaves.add(contractIdSlave);

        strategyId = UUID.randomUUID();
        //смотрим версию и базовую валюту в GRPC middle
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositionsBefore = grpcMiofRequest(contractIdSlave);
        int versionMiddle = clientPositionsBefore.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = steps.getBaseMoneyFromMiddle(clientPositionsBefore, "RUB");
        String baseMoney = Double.toString(middleQuantityBaseMoney);
        //создаем записи в client и contract для slave
        steps.createContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
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
    @AllureId("1481355")
    @DisplayName("C1481355.HandleLimitEvent. base_currency = USD. adjust_currency < 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1481355() {
        String SIEBEL_ID_SLAVE = "1-EYLV5GT";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        UUID investIdSlave = UUID.fromString("55043eac-480a-4fdb-b8d8-8e25d8faf576");
        contractIdSlave = "2000002739";
        String clientCodeSlave = "ADA513612556";
        contractIdSlaves.add(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, quantitySBER,
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, quantitySU29009RMFS6);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);

        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "USD", -1);
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
            .filter(ls -> ls.getCurrency().equals("USD"))
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
    @AllureId("1034763")
    @DisplayName("C1034763.HandleLimitEvent.[depo_limit|money_limit].open_balance_value + [depo_limit|money_limit].locked_value >= 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1034763() {
        String SIEBEL_ID_SLAVE = "5-234WH5E6W";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        contractIdSlaves.add(contractIdSlave);
        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, quantitySBER,
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, quantitySU29009RMFS6);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);

        steps.getClientAdjustCurrencyMiof(clientCodeSlave, contractIdSlave, "USD", 1);

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event eventBlock = null;
        for (int i = 0; i < messages.size(); i++) {
            Tracking.Event event = Tracking.Event.parseFrom(messages.get(i).getValue());
            if (event.getContract().getBlocked() == true) {
                eventBlock = event;
            }
        }
        log.info("Событие  в tracking.contract.event:  {}", eventBlock);
        //проверяем, данные в сообщении
        assertThat("action события не равен", eventBlock.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (eventBlock.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (eventBlock.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (eventBlock.getContract().getBlocked()), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1346552")
    @DisplayName("C1346552.HandleLimitEvent.Первичная инициализация пустого портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1346552() {
        String siebelIdSlave = "4-KNCGQPL";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        UUID investIdSlave = UUID.fromString("f17ace0f-8604-4df4-989f-c2e944633afa");
//        contractIdSlave = "2000076964";
        contractIdSlaves.add(contractIdSlave);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        steps.createEventInTrackingEvent(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddleAfter));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
        assertThat("Action не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getAction().getAction().name(), is("TRACKING_STATE_UPDATE"));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("версия не равна", slavePortfolio.getVersion(), is(versionMiddleAfter));
        assertThat("версия мастера не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("quantity baseMoney не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("0"));
        assertThat("ticker позиции не равен", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount позиции не равен", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("quantity позиции не равен", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("0"));
        assertThat("rate позиции не равен", slavePortfolio.getPositions().get(0).getRate().toString(), is("0"));
        assertThat("quantityDiff позиции не равен", slavePortfolio.getPositions().get(0).getQuantityDiff().toString(), is("0.0000"));
    }


    @SneakyThrows
    @Test
    @AllureId("1510531")
    @DisplayName("C1510531.HandleLimitEvent.Нулевая базовая валюта, если получили пустое событие")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1510531() {
        String siebelIdSlave = "5-2IE9J8C1S";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
//        UUID investIdSlave = UUID.fromString("f17ace0f-8604-4df4-989f-c2e944633afa");
//        contractIdSlave = "2000076964";
        contractIdSlaves.add(contractIdSlave);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd,ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из топика tracking.slave.command
        OffsetDateTime now = OffsetDateTime.now();
        Limit.Event eventLimit = Limit.Event.newBuilder()
            //.addAllMoneyLimit(moneyLimits)
            .setAction(limit.Limit.Event.Action.TRACKING_STATE_UPDATE)
            .setVersion(Int32Value.newBuilder().setValue(1).build())
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", eventLimit);
        byte[] eventBytes = eventLimit.toByteArray();
        oldKafkaService.send(MIOF_POSITIONS_RAW, contractIdSlave, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
//        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
//        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(1));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
        assertThat("Action не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getAction().getAction().name(), is("TRACKING_STATE_UPDATE"));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("версия не равна", slavePortfolio.getVersion(), is(1));
        assertThat("версия мастера не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("quantity baseMoney не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("0"));
        assertThat("ticker позиции не равен", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount позиции не равен", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("quantity позиции не равен", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("0"));
        assertThat("rate позиции не равен", slavePortfolio.getPositions().get(0).getRate().toString(), is("0"));
        assertThat("quantityDiff позиции не равен", slavePortfolio.getPositions().get(0).getQuantityDiff().toString(), is("0.0000"));
    }




    @SneakyThrows
    @Test
    @AllureId("1481233")
    @DisplayName("C1481233.HandleLimitEvent.Отрицательные лимиты по рублевой позиции.Base_currency = USD")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1481233() {
        String siebelIdSlave = "1-3L0X4M1";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
//        UUID investIdSlave = UUID.fromString("f17ace0f-8604-4df4-989f-c2e944633afa");
//        contractIdSlave = "2000076964";
        contractIdSlaves.add(contractIdSlave);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd,ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из топика tracking.slave.command
        OffsetDateTime now = OffsetDateTime.now();
        List<Limit.MoneyLimit> moneyLimits = new ArrayList<>();
        moneyLimits.add(Limit.MoneyLimit.newBuilder()
            .setLoadDate(18949)
            .setClientCode(clientCodeSlave)
            .setCurrency("RUB")
            .setFirmId("MC0253200000")
            .setOpenBalance(-3)
            .setOpenLimit(-3)
            .setOpenBalanceValue(Limit.Decimal.newBuilder()
                .setUnscaled(-3)
                .setScale(0))
            .setOpenLimitValue(Limit.Decimal.newBuilder()
                .setUnscaled(-3)
                .setScale(0))
            .setAccountId("MB9885503216").build());
        moneyLimits.add(Limit.MoneyLimit.newBuilder()
            .setLoadDate(18949)
            .setClientCode(clientCodeSlave)
            .setCurrency("USD")
            .setFirmId("MC0253200000")
            .setOpenBalance(50.17)
            .setOpenLimit(50.15)
            .setOpenBalanceValue(Limit.Decimal.newBuilder()
                .setUnscaled(5017)
                .setScale(2))
            .setOpenLimitValue(Limit.Decimal.newBuilder()
                .setUnscaled(5017)
                .setScale(2))
            .setAccountId("MB9885503216").build());
        Limit.Event eventLimit = Limit.Event.newBuilder()
            .addAllMoneyLimit(moneyLimits)
            .setAction(limit.Limit.Event.Action.TRACKING_STATE_UPDATE)
            .setVersion(Int32Value.newBuilder().setValue(1).build())
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", eventLimit);
        byte[] eventBytes = eventLimit.toByteArray();
        oldKafkaService.send(MIOF_POSITIONS_RAW, contractIdSlave, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
//        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
//        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(1));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
        assertThat("Action не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getAction().getAction().name(), is("TRACKING_STATE_UPDATE"));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("версия не равна", slavePortfolio.getVersion(), is(1));
        assertThat("версия мастера не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("quantity baseMoney не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("50.17"));
        assertThat("ticker позиции не равен", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount позиции не равен", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("quantity позиции не равен", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("0"));
        assertThat("rate позиции не равен", slavePortfolio.getPositions().get(0).getRate().toString(), is("0"));
        assertThat("quantityDiff позиции не равен", slavePortfolio.getPositions().get(0).getQuantityDiff().toString(), is("0.0000"));
    }


    @SneakyThrows
    @Test
    @AllureId("1481201")
    @DisplayName("C1481201.HandleLimitEvent.Отрицательные лимиты по рублевой позиции.Base_currency = RUB")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1481201() {
        String siebelIdSlave = "5-2LODDYVDX";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
//        UUID investIdSlave = UUID.fromString("f17ace0f-8604-4df4-989f-c2e944633afa");
//        contractIdSlave = "2000076964";
        contractIdSlaves.add(contractIdSlave);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub,ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из топика tracking.slave.command
        OffsetDateTime now = OffsetDateTime.now();
        List<Limit.MoneyLimit> moneyLimits = new ArrayList<>();
        moneyLimits.add(Limit.MoneyLimit.newBuilder()
            .setLoadDate(18949)
            .setClientCode(clientCodeSlave)
            .setCurrency("RUB")
            .setFirmId("MC0253200000")
            .setOpenBalance(-3)
            .setOpenLimit(-3)
            .setOpenBalanceValue(Limit.Decimal.newBuilder()
                .setUnscaled(-3)
                .setScale(0))
            .setOpenLimitValue(Limit.Decimal.newBuilder()
                .setUnscaled(-3)
                .setScale(0))
            .setAccountId("MB9885503216").build());
        Limit.Event eventLimit = Limit.Event.newBuilder()
            .addAllMoneyLimit(moneyLimits)
            .setAction(limit.Limit.Event.Action.TRACKING_STATE_UPDATE)
            .setVersion(Int32Value.newBuilder().setValue(1).build())
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", eventLimit);
        byte[] eventBytes = eventLimit.toByteArray();
        oldKafkaService.send(MIOF_POSITIONS_RAW, contractIdSlave, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
//        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
//        int versionMiddleAfter = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(1));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
        assertThat("Action не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getAction().getAction().name(), is("TRACKING_STATE_UPDATE"));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("версия не равна", slavePortfolio.getVersion(), is(1));
        assertThat("версия мастера не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("quantity baseMoney не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("-3"));
        assertThat("ticker позиции не равен", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount позиции не равен", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("quantity позиции не равен", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("0"));
        assertThat("rate позиции не равен", slavePortfolio.getPositions().get(0).getRate().toString(), is("0"));
        assertThat("quantityDiff позиции не равен", slavePortfolio.getPositions().get(0).getQuantityDiff().toString(), is("0.0000"));
    }




    @SneakyThrows
    @Test
    @AllureId("1507503")
    @DisplayName("C1507503.HandleLimitEvent.Не исключаем из массива позицию с currency = 'RUB' если она не является базовой валютой стратегии")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C1507503() {
        String SIEBEL_ID_SLAVE = "5-1DNCKYI7B";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
         String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        contractIdSlaves.add(contractIdSlave);
        strategyId = UUID.fromString("313644e0-f605-4ad5-b38b-f770ab9f0bbb");
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.slave, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositionsOne(date, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "151.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, ContractRole.slave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions = grpcMiofRequest(contractIdSlave);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //вычитываем все события из топика tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        // отправляем событие в miof.positions.raw
        byte[] eventBytes = createLimitFromMiddle(clientCodeSlave, versionMiddle).toByteArray();
        oldKafkaService.send(MIOF_POSITIONS_RAW, contractIdSlave, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        Tracking.PortfolioCommand portfolioCommand = getMessageFromKafka(TRACKING_SLAVE_COMMAND);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        //проверяем, данные в команде
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("Version не равен", portfolioCommand.getPortfolio().getVersion(), is(versionMiddle));
        assertThat("delayed_correction не равен", portfolioCommand.getPortfolio().getDelayedCorrection(), is(false));
        assertThat("Action не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getAction().getAction().name(), is("TRACKING_STATE_UPDATE"));
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(instrument.tickerRUB));
        assertThat("tradingClearingAccount не равен", portfolioCommand.getPortfolio().getPosition(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountRUB));
        assertThat("Quantity не равен", portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled(), is(0L));
        assertThat("ticker не равен", portfolioCommand.getPortfolio().getPosition(1).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount не равен", portfolioCommand.getPortfolio().getPosition(1).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity не равен", portfolioCommand.getPortfolio().getPosition(1).getQuantity().getUnscaled(), is(2L));
        assertThat("Quantity BaseMoney не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled(), is(5017L));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("версия не равна", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("версия мастера не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("quantity baseMoney не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("50.17"));
//        List<SlavePortfolio.Position> positionRUB = slavePortfolio.getPositions().stream()
//            .filter(ps -> ps.getTicker().equals(tickerRUB))
//            .collect(Collectors.toList());
        //RUB отфильтруем в slave
        assertThat("ticker позиции не равен", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount позиции не равен", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("quantity позиции не равен", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
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


    Limit.Event createLimitFromMiddle(String clientCodeSlave, int versionMiddle) {
        OffsetDateTime now = OffsetDateTime.now();
        Limit.DepoLimit depoLimit = Limit.DepoLimit.newBuilder()
            .setLoadDate(18949)
            .setClientCode(clientCodeSlave)
            .setSecCode(instrument.tickerAAPL)
            .setAccountId(instrument.tradingClearingAccountAAPL)
            .setFirmId("MC0253200000")
            .setOpenBalance(2.0)
            .setOpenLimit(2.0)
            .setOpenBalanceValue(Limit.Decimal.newBuilder()
                .setUnscaled(2)
                .setScale(0))
            .setOpenLimitValue(Limit.Decimal.newBuilder()
                .setUnscaled(2)
                .setScale(0))
            .build();
        List<Limit.MoneyLimit> moneyLimits = new ArrayList<>();
        moneyLimits.add(Limit.MoneyLimit.newBuilder()
            .setLoadDate(18949)
            .setClientCode(clientCodeSlave)
            .setCurrency("RUB")
            .setFirmId("MC0253200000")
            .setOpenBalance(0.0)
            .setOpenLimit(0.0)
            .setOpenBalanceValue(Limit.Decimal.newBuilder()
                .setUnscaled(0)
                .setScale(0))
            .setOpenLimitValue(Limit.Decimal.newBuilder()
                .setUnscaled(0)
                .setScale(0))
            .setAccountId("MB9885503216").build());
        moneyLimits.add(Limit.MoneyLimit.newBuilder()
            .setLoadDate(18949)
            .setClientCode(clientCodeSlave)
            .setCurrency("USD")
            .setFirmId("MC0253200000")
            .setOpenBalance(50.17)
            .setOpenLimit(50.17)
            .setOpenBalanceValue(Limit.Decimal.newBuilder()
                .setUnscaled(5017)
                .setScale(2))
            .setOpenLimitValue(Limit.Decimal.newBuilder()
                .setUnscaled(5017)
                .setScale(2))
            .setAccountId("MB9885503216").build());
        Limit.Event eventLimit = Limit.Event.newBuilder()
            .addDepoLimit(depoLimit)
            .addAllMoneyLimit(moneyLimits)
            .setAction(limit.Limit.Event.Action.TRACKING_STATE_UPDATE)
            .setVersion(Int32Value.newBuilder().setValue(versionMiddle).build())
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", eventLimit);
        return eventLimit;

    }

}
