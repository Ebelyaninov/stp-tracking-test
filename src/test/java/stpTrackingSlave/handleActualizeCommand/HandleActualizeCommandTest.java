package stpTrackingSlave.handleActualizeCommand;

import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Repeat;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
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
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию")
@Feature("TAP-6864")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleActualizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    GrpcServicesAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class

})
public class HandleActualizeCommandTest {
    @Autowired
    MiddleGrpcService middleGrpcService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingSlaveSteps steps;


    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Contract contract;
    Client clientSlave;
    String contractIdMaster;
    Subscription subscription;


    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";

    String tickerABBV = "ABBV";
    String classCodeABBV = "SPBXM";
    String tradingClearingAccountABBV = "TKCBM_TCAB";

    String tickerYNDX = "YNDX";
//    String tradingClearingAccountYNDX = "L01+00000F00";
    String tradingClearingAccountYNDX = "Y02+00001F00";
    String classCodeYNDX = "TQBR";

    String tickerSBER = "SBER";
    String tradingClearingAccountSBER = "L01+00002F00";
    String classCodeSBER = "TQBR";

    String tickerUSD = "USDRUB";
    String tradingClearingAccountUSD = "MB9885503216";
//    String tradingClearingAccountUSD = "MB0253214128";
    String classCodeUSD = "EES_CETS";

    String tickerGBP = "GBPRUB";
    String tradingClearingAccountGBP = "MB9885503216";
//    String tradingClearingAccountGBP = "MB0253214128";
    String classCodeGBP = "EES_CETS";

    String tickerCHF = "CHFRUB";
    String tradingClearingAccountCHF = "MB9885503216";
//    String tradingClearingAccountCHF = "MB0253214128";
    String classCodeCHF = "EES_CETS";

    String tickerHKD = "HKDRUB";
    String tradingClearingAccountHKD = "MB9885503216";
//    String tradingClearingAccountHKD = "MB0253214128";
    String classCodeHKD = "EES_CETS";

    String tickerFB = "FB";
    String classCodeFB = "SPBXM";
    String tradingClearingAccountFB = "TKCBM_TCAB";
//   String tradingClearingAccountFB = "L01+00000SPB";

    String contractIdSlave;
    UUID strategyId;
    UUID strategyIdNew;
    String SIEBEL_ID_MASTER = "1-3Z0IR7O";
    String SIEBEL_ID_SLAVE = "5-JEF71TBN";

    public String value;

    String description = "description test стратегия autotest update adjust base currency";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                subscriptionService.deleteSubscription(subscription);
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
                trackingService.deleteStrategy(steps.strategy);
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
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyIdNew);
            } catch (Exception e) {
            }
        });
    }



    //д.б. USD= 7000 в мидл
    @SneakyThrows
    @Test
    @AllureId("731513")
    @DisplayName("C731513.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля с базовой валютой")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731513() {
        String SIEBEL_ID_SLAVE = "1-FRT3HXX";
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, versionMiddle-2, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        // рассчитываем значение lots
        BigDecimal lots = slavePortfolio.getPositions().get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(versionMiddle-2, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
        steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
    }

    //пустой список позиций 0 - по деньгам в мидл
    @SneakyThrows
    @Test
    @AllureId("1366344")
    @DisplayName("C1366344.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация пустого slave-портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1366344() {
        String SIEBEL_ID_SLAVE = "5-3NRSEZFX";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster,null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(versionMiddle)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                        .build())
                    .build())
                .setDelayedCorrection(false)
                .build())
            .build();
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
    }




//    @SneakyThrows
//    @Test
//    @AllureId("731513")
//    @DisplayName("C731513.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля с базовой валютой")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
//    void C731513_111() {
//        int randomNumber = 0 + (int) (Math.random() * 100);
//        String title = "Autotest" +String.valueOf(randomNumber);
//        String description = "description test стратегия autotest update adjust base currency";
//        BigDecimal lot = new BigDecimal("1");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.randomUUID();
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        //получаем текущую дату
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        // создаем портфель для master в cassandra
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
//        //создаем подписку на стратегию для slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        long subscriptionId = subscription.getId();
//        //формируем команду на актуализацию для slave
//        //передаем только базовую валюту
//        OffsetDateTime time = OffsetDateTime.now();
//        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
//            contractIdSlave, 1, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
//        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
//        Thread.sleep(2000);
//        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
//        //получаем портфель мастера
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
//        //получаем портфель slave
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        //получаем значение price из кеша exchangePositionPriceCache
//        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
//        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
//        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity();
//        BigDecimal slavePositionRate = BigDecimal.ZERO;
//        BigDecimal quantityDiff = (masterPositionRate.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
//        //проверяем значение портфеля slave
//        checkSlavePortfolioParameters(1, 3, "7000");
//        assertThat("Время changed_at для base_money_position не равно", slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        checkPositionParameters(0, ticker, tradingClearingAccount, "0", price, slavePositionRate, masterPositionRate,
//            quantityDiff, null);
//        // рассчитываем значение lots
//        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
//        BigDecimal priceAsk = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
//        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
//            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
//            .multiply(new BigDecimal("0.01"));
//        //проверяем значения в slaveOrder
//        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
//        checkOrderParameters(1, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
//        steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
//    }
//


   //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @AllureId("741543")
    @DisplayName("C741543.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля c позицией")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C741543() {
        String SIEBEL_ID_SLAVE = "1-1Q5Z83F";
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = new BigDecimal("2").multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем значение портфеля slave
        checkSlavePortfolioParameters(versionMiddle, 2, "7000");
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "null");
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(versionMiddle, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
    }

    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @AllureId("1416943")
    @DisplayName("C1416943.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля c пустой позицией")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1416943() {
        String SIEBEL_ID_SLAVE = "1-1Q5Z83F";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMinutes(7));
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(7);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMinutes(5);
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();

        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setQuantity(Tracking.Decimal.newBuilder().build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                .build())
            .build();
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(0)
            .setUnscaled(7000)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(versionMiddle)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                        .build())
                    .build())
                .addPosition(position)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                        .build())
                    .build())
                .setDelayedCorrection(false)
                .build())
            .build();
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();

        BigDecimal slavePosQuantity = new BigDecimal("2").multiply(price);

        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем значение портфеля slave
        checkSlavePortfolioParameters(versionMiddle, 2, "7000");
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "39");
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(true));
    }


    // д.б. AAPL=2 USD=0
    @SneakyThrows
    @Test
    @AllureId("748732")
    @DisplayName("C748732.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля, не передан параметр base_money_position")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C748732() {
        String SIEBEL_ID_SLAVE = "1-4ILVF6E";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();

        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = new BigDecimal("2").multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем значение портфеля slave
        checkSlavePortfolioParameters(versionMiddle, 3, "0");
        assertThat("changed_at для base_money_position в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(), is(nullValue()));
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "39");
    }


    @SneakyThrows
    @Test
    @AllureId("1053004")
    @DisplayName("1053004.HandleActualizeCommand.Обрабатываем версии.Получение измененных позиций из Middle.Cохранение в кэш actualizeCommandCache")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля= 1," +
        " Action = 'MORNING_UPDATE',version из команды < version из ответа ")
    void C1053004() {
        String SIEBEL_ID_SLAVE = "1-1IE1IUG";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerYNDX, tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle - 1,
            steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.MORNING_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle - 2));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySl));
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1054936")
    @DisplayName("1054936.HandleActualizeCommand.Все изменения отражены в команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    void C1054936() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 588486,
            contractIdSlave, versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        checkSlavePortfolioParameters(versionMiddle, 3, "5884.86");
        assertThat("lastChangeAction BaseMoney не равно", slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 12));
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("5"));
        assertThat("lastChangeAction Position не равно", slavePortfolio.getPositions().get(0).getLastChangeAction(), is((byte) 12));
    }


    @SneakyThrows
    @Test
    @AllureId("1057608")
    @DisplayName("1057608.HandleActualizeCommand.Команда по отложенным изменениям - параметр delayed_correction = true")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля  > 1, delayed_correction = true")
    void C1057608() {
        String SIEBEL_ID_SLAVE = "1-1IE1IUG";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //получаем базовую валюту
        double middleQuantityBaseMoney = getBaseMoneyFromMiddle(clientPositions, "RUB");
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerYNDX, tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle - 1,
            steps.createPosInCommand(tickerYNDX, tradingClearingAccountYNDX, 2, Tracking.Portfolio.Action.MORNING_UPDATE), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle - 2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle - 2), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle - 2));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySl));
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
        //формируем новую команду на актуализацию для slave

        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyBaseMoney(2, 588486, contractIdSlave,
            versionMiddle, time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, true);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(middleQuantityBaseMoney));
    }

    //должно быть 4 яндексов по позициям в мидле
    @SneakyThrows
    @Test
    @AllureId("1365098")
    @DisplayName("1365098.HandleActualizeCommand.Команда по отложенным изменениям - параметр delayed_correction = true," +
        "если кеш actualizeCommandCache пустой и упущена максимум одна версия")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля  > 1, delayed_correction = true")
    void C1365098() {
        String SIEBEL_ID_SLAVE = "5-3HYUEXL7";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //получаем базовую валюту
        double middleQuantityBaseMoney = getBaseMoneyFromMiddle(clientPositions, "RUB");
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerYNDX, tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем  команду на актуализацию для slave
        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyBaseMoney(2, 500000, contractIdSlave,
            versionMiddle, time, Tracking.Portfolio.Action.ADJUST, true);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(middleQuantityBaseMoney));
        List<SlavePortfolio.Position> positionYNDX = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerYNDX))
            .collect(Collectors.toList());
        checkPosition(positionYNDX, tickerYNDX, tradingClearingAccountYNDX, "4");
    }




    @SneakyThrows
    @Test
    @AllureId("1062109")
    @DisplayName("C1062109.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если позиция и базовая валюта изменились по команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1062109() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,4), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(4));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 12));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            //если это позиция, по которой пришла актуализация по команде, проверяем изменения
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
                assertThat("quantity позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getQuantity().toString(), is("5"));
                assertThat("lastChangeAction позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getLastChangeAction(), is((byte) 12));
            }
            //если это позиция, без изменений
            if (tickerABBV.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
                assertThat("quantity позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getQuantity().toString(), is("1"));
                assertThat("lastChangeAction позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getLastChangeAction(), is(nullValue()));
            }
        }
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


   //AAPL=4 USD=5855.6
    @SneakyThrows
    @Test
    @AllureId("1516525")
    @DisplayName("C1516525.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1516525() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle-4, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 4, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder.getVersion(), is(versionMiddle));
        assertThat("AttemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(new BigDecimal("1")));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerABBV));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder.getCreateAt(), is(notNullValue()));
    }


    //AAPL=4 USD=5855.6
    @SneakyThrows
    @Test
    @AllureId("1517499")
    @DisplayName("C1517499.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на другую стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1517499() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        strategyIdNew = UUID.randomUUID();
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerFB, tradingClearingAccountFB,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyIdNew, versionMiddle-1, 5,
            baseMoneySlave, date, createListSlavePos);

        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 4, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
//        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
//            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 39));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder.getVersion(), is(versionMiddle));
        assertThat("AttemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(new BigDecimal("1")));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerABBV));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder.getCreateAt(), is(notNullValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("1517779")
    @DisplayName("C1517779.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию.Только базовая валюта")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1517779() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle-1, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();

        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(1, 58556,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);

        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
//        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
//            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 39));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is(nullValue()));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder.getVersion(), is(versionMiddle));
        assertThat("AttemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(new BigDecimal("1")));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerABBV));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder.getCreateAt(), is(notNullValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("1518153")
    @DisplayName("C1518153.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию.Нулевые значения в команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1518153() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle-1, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 0, contractIdSlave,
            versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 0, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
//        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
//            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt(), is(nullValue()));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
    }

    @SneakyThrows
    @Test
    @AllureId("1518223")
    @DisplayName("C1518223.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию.Только позиция")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1518223() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 4,
            steps.createPosInCommand(ticker, tradingClearingAccount, 4, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,4), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(4));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(),  is(nullValue()));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder.getVersion(), is(4));
        assertThat("AttemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder.getIdempotencyKey(), is(notNullValue()));
//        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(new BigDecimal("4")));
//        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
//        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder.getCreateAt(), is(notNullValue()));
    }







    @SneakyThrows
    @Test
    @AllureId("1063048")
    @DisplayName("C1063048.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если базовая валюта не изменились по команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1063048() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave,
            4, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,4), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(4));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6551.10"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    //по договору д.б. 100 USD и 2 AAPL
    @SneakyThrows
    @Test
    @AllureId("1518740")
    @DisplayName("C1518740.HandleActualizeCommand.Version из команды - slave_portfolio.version текущего портфеля  > , action = ADJUST")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1518740() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
      //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time,Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(),
            is(nullValue()));
        assertThat("ticker позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("TradingClearingAccount позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
    }



    //по договору д.б. 100 USD и 2 AAPL
    @SneakyThrows
    @Test
    @AllureId("1052370")
    @DisplayName("C1052370.HandleActualizeCommand.Первичная инициализация портфеля slave, action != TRACKING_STATE_UPDATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1052370_111() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle-2, 4,
            baseMoneySlave, date, createListSlavePos);

        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time,Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(),
            is(nullValue()));
        assertThat("ticker позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("TradingClearingAccount позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
    }


//
//    @SneakyThrows
//    @Test
//    @AllureId("1065723")
//    @DisplayName("C1065723.HandleActualizeCommand.Формирование актуального набора позиций, " +
//        "если узнали об изменении позиции и базовой валюте из gRPC middle")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
//    void C1065723() {
//        String SIEBEL_ID_SLAVE = "5-167ET5VFO";
//        String title = "тест стратегия autotest update base currency";
//        String description = "description test стратегия autotest update adjust base currency";
//        String ticker2 = "FB";
//        String classCode2 = "SPBXM";
//        String tradingClearingAccount2 = "L01+00000SPB";
//        BigDecimal lot = new BigDecimal("1");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту slave в БД сервиса счетов
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//       contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.randomUUID();
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
//            null, ContractState.untracked, strategyId, title, description,
//            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker2, tradingClearingAccount2,
//            "20", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
//        //создаем подписку на стратегию для slave
//        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
//        int versionMiddle = 15;
//        // создаем портфель slave с позицией в кассандре
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker2, tradingClearingAccount2,
//            "10", date);
//        String baseMoneySl = "3000.0";
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
//            baseMoneySl, date, createListSlaveOnePos);
//        OffsetDateTime time = OffsetDateTime.now();
//        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
//            versionMiddle - 1, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
//                Tracking.Portfolio.Action.MORNING_UPDATE), time, Tracking.Portfolio.Action.MORNING_UPDATE, false);
//        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
//        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
//        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle - 2));
//        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySl));
//        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
//        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
//        //формируем новую команду на актуализацию для slave
//        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
//            steps.createPosInCommand(ticker2, tradingClearingAccount2, 20, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
//            time, true);
//
//        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
//        checkComparedSlaveVersion(versionMiddle);
//        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
//        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
//
//
//    }

    //необходимо отрицательное значение по USD, 2-AAPL
    @SneakyThrows
    @Test
    @AllureId("1481900")
    @DisplayName("C1481900.HandleActualizeCommand.Формирование актуального списка позиций из Middle." +
        "Отрицательное значение по базовой валюте")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1481900() {
        //String SIEBEL_ID_SLAVE = "5-CKWQPRIV";
        String SIEBEL_ID_SLAVE = "5-EJVMT8JB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "1", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkSlavePortfolioVersion(versionMiddle);
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("-100"));
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerFB))
            .collect(Collectors.toList());
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("2"));
//        assertThat("Quantity позиции в портфеле slave не равна", positionFB.get(0).getQuantity().toString(), is("1"));
    }


    //необходимо отрицательное значение по RUB, 2-AAPL, 100-USD
    @SneakyThrows
    @Test
    @AllureId("1481454")
    @DisplayName("C1481454.HandleActualizeCommand.Формирование актуального списка позиций из Middle." +
        "Отрицательное значение по money.currency = 'RUB'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1481454() {
        String SIEBEL_ID_SLAVE = "5-340W7W4R";
       // String SIEBEL_ID_SLAVE = "5-DXA6EWR9";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);

        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "1", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkSlavePortfolioVersion(versionMiddle);
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("2"));
        assertThat("Количество позиций в портфеле slave не равна", slavePortfolio.getPositions().size(), is(1));
    }



    @SneakyThrows
    @Test
    @AllureId("742580")
    @DisplayName("C742580.HandleActualizeCommand.Актуализация портфеля, без выставления заявки")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742580() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,4), notNullValue());
        checkSlavePortfolioParameters(4, 4, "5855.6");
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем значение changed_at у позиции
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            }
            if (tickerABBV.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            }
        }
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }



    @SneakyThrows
    @Test
    @AllureId("731504")
    @DisplayName("C731504.HandleActualizeCommand.Получение подтверждения в полном объеме")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731504() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 3,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "5855.6");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff, "12");
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
    }

    @SneakyThrows
    @Test
    @AllureId("1333799")
    @DisplayName("C1333799.HandleActualizeCommand.Получение подтверждения в полном, если в команде есть позиция с action MONEY_SELL_TRADE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1333799() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster,null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
//         создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "3825.9";
        int version = 1;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPos(tickerUSD, tradingClearingAccountUSD,
            "8",  new BigDecimal("65.4400000000"), new BigDecimal("0.1204"),
            new BigDecimal("-0.1204"), new BigDecimal("-8.0021"),tickerYNDX, tradingClearingAccountYNDX,
            "0",  new BigDecimal("6133.4"), new BigDecimal("0"),
            new BigDecimal("0.8240"), new BigDecimal("0.5843"), date, 1);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 2,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("8");
        BigDecimal positionQuantity = new BigDecimal("0");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantityCrTime(contractIdSlave, strategyId, 1, 1,
            1, classCodeUSD, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("65.31"), slavePosQuantityBefore,
            null, tickerUSD, tradingClearingAccountUSD);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 434827, contractIdSlave,
            version+1, steps.createPosInCommand(tickerUSD, tradingClearingAccountUSD, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,2), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
//        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 1, Byte.valueOf("1"));
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
    }



    @SneakyThrows
    @Test
    @AllureId("1366347")
    @DisplayName("C1366347.HandleActualizeCommand.Получение подтверждения в полном, если в команде есть позиция с action MONEY_SELL_TRADE для GBP")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1366347() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
//         создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave,null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "3825.9";
        int version = 1;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPos(tickerGBP, tradingClearingAccountGBP,
            "8",  new BigDecimal("92.5225"), new BigDecimal("0.1204"),
            new BigDecimal("-0.1204"), new BigDecimal("-8.0021"),tickerYNDX, tradingClearingAccountYNDX,
            "0",  new BigDecimal("6133.4"), new BigDecimal("0"),
            new BigDecimal("0.8240"), new BigDecimal("0.5843"), date, 1);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 2,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("8");
        BigDecimal positionQuantity = new BigDecimal("0");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantityCrTime(contractIdSlave, strategyId, 1, 1,
            1, classCodeGBP, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("65.31"), slavePosQuantityBefore,
            null, tickerGBP, tradingClearingAccountGBP);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 434827, contractIdSlave,
            version+1, steps.createPosInCommand(tickerGBP, tradingClearingAccountGBP, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,2), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 1, Byte.valueOf("1"));
//        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
    }



    @SneakyThrows
    @Test
    @AllureId("1333801")
    @DisplayName("C1333801.HandleActualizeCommand.Подтверждение по отклоненной заявке, полный объем заявки еще не подтвержден," +
        " в команде есть позиция с action MONEY_SELL_TRADE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1333801() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster,null, contractIdMaster,null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave,null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("275");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(tickerUSD, tradingClearingAccountUSD,
            "275", date, 1, new BigDecimal("65.71"), new BigDecimal("0.8253"),
            new BigDecimal("-0.8253"), new BigDecimal("-275.0098"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("65.71"), new BigDecimal("275"),
            null, tickerUSD, tradingClearingAccountUSD);
        //формируем команду на актуализацию для slave
        BigDecimal positionQuantityCommand = new BigDecimal("200");
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(tickerUSD, tradingClearingAccountUSD, 200,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantityCommand.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("State не равно", slaveOrder.getState(), is(nullValue()));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
    }


    @SneakyThrows
    @Test
    @AllureId("856826")
    @DisplayName("C856826.HandleActualizeCommand.Подтверждение по отклоненной заявке, полный объем заявки еще не подтвержден")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C856826() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        BigDecimal positionQuantityCommand = new BigDecimal("2");
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 2,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "5855.6");
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price, slavePositionRate, rateDiff,
            quantityDiff, "12");
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantityCommand.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("State не равно", slaveOrder.getState(), is(nullValue()));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
    }



    @SneakyThrows
    @Test
    @AllureId("1071599")
    @DisplayName("C1071599.HandleActualizeCommand.Получение подтверждения в полном объеме по одной позиции, " +
        "выставление новой заявки по другой позиции")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1071599() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 4,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        BigDecimal QuantityDiffticker = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerABBV.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(3, 3, "6855.6");
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker.toString(), is("0"));
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1"));
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));

//        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 3, Byte.valueOf("1"));
//        assertThat("State не равно", slaveOrder.getQuantity().toString(), is("1"));
//        assertThat("State не равно", slaveOrder.getTicker(), is(tickerABBV));
    }


    @SneakyThrows
    @Test
    @AllureId("1071663")
    @DisplayName("C1071663.HandleActualizeCommand.Получение подтверждения в полном объеме по одной позиции, blocked != false")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1071663() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 4,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, ticker, tradingClearingAccount);
        contract = contractService.updateBlockedContract(contractIdSlave, true);
        steps.createEventInTrackingEventWithBlock(contractIdSlave, true);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        BigDecimal QuantityDiffticker = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(3, 3, "6855.6");
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker.toString(), is("0"));
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1"));
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        //смотрим, что заявка не выставлялась
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrderWithVersionAndAttemps(contractIdMaster, strategyId, 3, Byte.valueOf("1"));
        assertThat("запись по заявке не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @Repeat(value = 3)
    @AllureId("742614")
    @DisplayName("C742614.HandleActualizeCommand.Синхронизируем портфель, после актуализации")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742614() {
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(tickerABBV, tradingClearingAccountABBV, 1,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,4), notNullValue());
        BigDecimal QuantityDiffticker1 = BigDecimal.ZERO;
        BigDecimal QuantityDiffticker2 = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker1 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
            if (tickerABBV.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker2 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(4, 4, "5855.6");
        // рассчитываем значение;
        BigDecimal lots = QuantityDiffticker1.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(4, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
    }


    @SneakyThrows
    @Test
    @Repeat(value = 3)
    @AllureId("1366358")
    @DisplayName("C1366358.HandleActualizeCommand.Синхронизируем портфель, после актуализации с валютой")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1366358() {
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster,null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "5",date, 2,steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave,null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerHKD, tradingClearingAccountHKD,
            "27", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(tickerCHF, tradingClearingAccountCHF, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,4), notNullValue());
        BigDecimal QuantityDiffticker1 = BigDecimal.ZERO;
        BigDecimal QuantityDiffticker2 = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerHKD.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker1 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
            if (tickerYNDX.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker2 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(4, 4, "5855.6");
        // рассчитываем значение;
        BigDecimal lots = QuantityDiffticker1.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(tickerHKD, "bid"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(4, "1", lot, lots, priceBid, tickerHKD, tradingClearingAccountHKD, classCodeHKD);
    }



    @SneakyThrows
    @Test
    @AllureId("742634")
    @DisplayName("C742634.HandleActualizeCommand.Ожидаем подтверждение дальше," +
        " position.action NOT IN ('SECURITY_BUY_TRADE', 'SECURITY_SELL_TRADE')")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742634() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 3,
            steps.createPosInCommand(ticker, tradingClearingAccount, 5,
            Tracking.Portfolio.Action.COUPON_TAX), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff, "19");
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("найдена запись в masterPortfolio", slaveOrder.getState(), is(nullValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("1249143")
    @DisplayName("C1249143.HandleActualizeCommand.Проверка, можно ли запускать синхронизацию договора, если у подписки blocked = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1249143() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId,1, "7000",  positionList);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date,        1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        List<MasterPortfolio.Position> masterPosTwo = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6192.9", masterPosTwo);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
            String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            2, steps.createPosInCommand(tickerABBV, tradingClearingAccountABBV, 1,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,2), notNullValue());
        checkSlavePortfolioParameters(2,3,"5855.6");
    }

    @SneakyThrows
    @Test
    @AllureId("1365590")
    @DisplayName("С1365590.HandleActualizeCommand.Обрабатываем событие с незнакомым enum. Если value незнакомый, то не падаем в ошибку, а должны сохранять int")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE' и не нашли enumAction")
    void С1365590() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //ToDo для корректной работы теста, после изменения схемы, нужно добавить в enum Action значение TEST = 99; в схему tracking.proto
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 588486,
            contractIdSlave, versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.TEST), time, Tracking.Portfolio.Action.TEST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        checkSlavePortfolioParameters(versionMiddle, 3, "5884.86");
        assertThat("lastChangeAction BaseMoney не равно", slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) Tracking.Portfolio.Action.TEST.getNumber()));
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("5"));
        assertThat("lastChangeAction Position не равно", slavePortfolio.getPositions().get(0).getLastChangeAction(), is((byte) Tracking.Portfolio.Action.TEST.getNumber()));
    }


    @SneakyThrows
    @Test
    @AllureId("1365591")
    @DisplayName("С1365591. Получили не известный enum во врема синхронизации)")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1365591() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 3,
            steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.TEST), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff, String.valueOf(Tracking.Portfolio.Action.TEST.getNumber()));
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("найдена запись в masterPortfolio", slaveOrder.getState(), is(nullValue()));
    }


    //по договору д.б. 100 USD и 2 AAPL и RUB != 0
    @SneakyThrows
    @Test
    @AllureId("1523191")
    @DisplayName("C1523191.HandleActualizeCommand.Отфильтровываем RUB из ответа метода midle GRPC если получили RUB\n")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1523191() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time,Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        assertThat("ticker позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("TradingClearingAccount позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
        //Проверяем, что позициz RUB есть в midle
        await().atMost(FIVE_SECONDS).until(() ->
            getBaseMoneyFromMiddle(clientPositions, "RUB"), notNullValue());

        //Проверить, что не добавили позицию RUB
        boolean positionotFound =  slavePortfolio.getPositions().stream()
            .anyMatch(ps -> ps.getTicker().equals("RUB"));
        assertEquals(positionotFound, false);

        List<SlavePortfolio.Position> positionRUBNotFound =  slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals("RUB"))
            .collect(Collectors.toList());
        assertEquals(positionRUBNotFound.size(), 0);
    }


    @SneakyThrows
    @Test
    @AllureId("1523957")
    @DisplayName("C1523957.Инициализация портфеля slave данными из ответа метода midle GRPC с базовой валютой RUB")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1523957() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerYNDX, tradingClearingAccountYNDX,
            "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //Получаем базовую валюту по стратегии из midle
        int baseMoneySlave = (int) getBaseMoneyFromMiddle(clientPositions, "RUB");
        String baseMoneyPositionSlave = String.valueOf(baseMoneySlave);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time,Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneyPositionSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("731513")
    @DisplayName("C731513.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля с базовой валютой")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C73151322321() {
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 1, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        // рассчитываем значение lots
        BigDecimal lots = slavePortfolio.getPositions().get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(1, "0", lot, lots, priceOrder, ticker, tradingClearingAccountABBV, classCode);
        steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
    }

   //USD= 7000
    @SneakyThrows
    @Test
    @AllureId("1616397")
    @DisplayName("C1616397. Обновляем метку старта подписки в событии TRACKING_STATE_UPDATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1616397() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave с временем старта подписки -1c
        OffsetDateTime time = startSubTime.minusSeconds(1);
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        Subscription getDataFromSubscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //Проверяем обновление startTime подписки (-1c от даты старта подписки и +3ч)
        java.sql.Timestamp getNewStartedAt = new java.sql.Timestamp(time.toInstant().toEpochMilli());
        assertThat("Не обновили время подписки", getDataFromSubscription.getStartTime(), is(getNewStartedAt));
    }


    //по договору д.б. 1000 RUB и 10 SBER
    @SneakyThrows
    @Test
    @AllureId("1596739")
    @DisplayName("С1596739.Определяем актуальный список позиций в портфеле из Middle в событии TRACKING_STATE_UPDATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1596739() {
        String SIEBEL_ID_SLAVE = "5-3CGSIDQR";
        String baseMoneyPositionSlave = "1078.97";
        String quantityPos = "10";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMinutes(7));
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(7);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER,
            "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMinutes(5);
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем версию из middle через запрос по grpc
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave c action = TRACKING_STATE_UPDATE
        //указываем данные по baseMoney количество бумаг и версию не такие как в мидл
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 900,
            contractIdSlave, versionMiddle, steps.createPosInCommand(tickerSBER, tradingClearingAccountSBER, 20,
                Tracking.Portfolio.Action.TRACKING_STATE_UPDATE), time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем, что создался портфель для slave с данными актуальными из мидл по запросу grpc
        checkSlavePortfolioParameters(versionMiddle, 3, baseMoneyPositionSlave);
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(tickerSBER));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccountSBER));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
    }






    private static Stream<Arguments> secondsForPlus () {
        return Stream.of(
            Arguments.of(0, SubscriptionStatus.active),
            Arguments.of(1, SubscriptionStatus.active),
            Arguments.of(1, SubscriptionStatus.draft),
            Arguments.of(60, SubscriptionStatus.inactive)
        );
    }

    @ParameterizedTest
    @MethodSource("secondsForPlus")
    @SneakyThrows
    @AllureId("1616370")
    @DisplayName("C1616370. Не обновляем Метку старта подписки в событии TRACKING_STATE_UPDATE если start_time <= created_at или статус подписки draft")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1616370(int plusSeconds, SubscriptionStatus subscriptionStatus) {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();

        if (subscriptionStatus.equals(SubscriptionStatus.inactive)){
            OffsetDateTime endSubTime = OffsetDateTime.now();
            steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
                strategyId, subscriptionStatus,  new java.sql.Timestamp(startSubTime.minusSeconds(30).toInstant().toEpochMilli()),
                new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        }
        else {
            steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
                strategyId, subscriptionStatus,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
                null, false);
        }
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем дату старта подписки
        java.sql.Timestamp subscriptionStartTime = subscription.getStartTime();
        //формируем команду на актуализацию, для slave с временем старта подписки 0c И +1с \ -1c но статус подписки draft \ inactive
        OffsetDateTime createdAt;
        if (subscriptionStatus.equals(SubscriptionStatus.active)) {
            createdAt  = startSubTime.plusSeconds(plusSeconds);
        }
        else {
            createdAt = startSubTime.minusSeconds(plusSeconds);
        }

        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 1, createdAt, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);

        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());

        Subscription getDataFromSubscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //Проверяем, что не обновили метку времени старта подписки
        assertThat("Не обновили время подписки", getDataFromSubscription.getStartTime(), is(subscriptionStartTime));
    }



    @SneakyThrows
    @Test
    @AllureId("1616399")
    @DisplayName("C1616399. Не удалось обновить метку start_time в подписке")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1616399() {
        String SIEBEL_ID_SLAVE = "1-FRT3HXX";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        String periodDefault = "[" + startSubTime.minusDays(2).toLocalDateTime() + "," + startSubTime.minusHours(1).toLocalDateTime() + ")";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //Создаем подписку за прошлый период
        subscription =  new Subscription()
            .setSlaveContractId(contractIdSlave)
            .setStrategyId(strategyId)
            .setStartTime(new java.sql.Timestamp(startSubTime.minusDays(2).toInstant().toEpochMilli()))
            .setEndTime(new java.sql.Timestamp(startSubTime.minusHours(2).toInstant().toEpochMilli()))
            .setStatus(SubscriptionStatus.inactive)
            .setBlocked(false);
        subscription = subscriptionService.saveSubscription(subscription);

        //формируем команду на актуализацию для slave с временем старта подписки
        OffsetDateTime time = startSubTime.minusHours(6);
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 1, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);

        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);

        await().atMost(FIVE_SECONDS).until(() ->
            contractService.getContract(contractIdSlave).getBlocked(), is(true));
        Contract getContract = contractService.getContract(contractIdSlave);
        //Проверяем блокировку контракта
        assertThat("Не заблокировали контракт", getContract.getBlocked(), is(true));
    }



    // методы для работы тестов*************************************************************************

    Tracking.PortfolioCommand createCommandActualizeOnlyBaseMoney(int scale, int unscaled, String contractIdSlave,
                                                                    int version, OffsetDateTime time,
                                                                    Tracking.Portfolio.Action action, boolean delayedCorrection) {
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }


    Tracking.PortfolioCommand createCommandActualizeWithPosition(int scale, int unscaled, String contractIdSlave,
                                                                    int version, Tracking.Portfolio.Position position,
                                                                    OffsetDateTime time, Tracking.Portfolio.Action action,
                                                                    boolean delayedCorrection) {
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())

            .build();
        return command;
    }


    Tracking.PortfolioCommand createCommandActualizeOnlyPosition(String contractIdSlave, int version,
                                                                   Tracking.Portfolio.Position position,
                                                                   OffsetDateTime time, boolean delayedCorrection) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }


    public void checkSlavePortfolioParameters(int version, int comparedToMasterVersion, String baseMoney) {
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(version));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(comparedToMasterVersion));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney));
    }


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal rateDiff, BigDecimal quantityDiff, String lastChangeAction) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        slavePortfolio.getPositions().get(pos).getRate().compareTo(slavePositionRate);
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
//        Byte value = lastChangeAction == null ? null : Byte.valueOf(lastChangeAction);
//        assertThat("lastChangeAction позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getLastChangeAction(), is(value));
    }


    public void checkOrderParameters(int version, String action, BigDecimal lot, BigDecimal lots,
                                     BigDecimal priceOrder, String ticker, String tradingClearingAccount,
                                     String classCode) {
        assertThat("Версия портфеля не равно", slaveOrder.getVersion(), is(version));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("price бумаги не равен", slaveOrder.getPrice(), is(priceOrder));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
    }


    void checkComparedSlaveVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(2000);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() == version) {
                i = 5;
            } else {
                break;
            }
        }
    }


    double getBaseMoneyFromMiddle(CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions, String curBaseMoney) {
        double middleQuantityBaseMoney = 0;
        //складываем позиции по валютам у которых kind =365 в map, в отдельную map складем базовую валюту и ее значение
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getMoneyCount(); i++) {
            //значение по базовой валюте кладем в middleQuantityBaseMoney
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (curBaseMoney.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                middleQuantityBaseMoney = (clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getScale()))
                    + (clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getScale()));
            }

        }
        return middleQuantityBaseMoney;
    }


    //проверяем параметры позиции
    public void checkPosition(List<SlavePortfolio.Position> position,  String ticker, String tradingClearingAccount, String quantity
                             ) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", position.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", position.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", position.get(0).getQuantity().toString(), is(quantity));

    }


    // ожидаем версию портфеля slave
    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3500);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                i = 5;
            }
        }
    }

    // ожидаем версию портфеля slave
    void checkSlavePortfolioVersion(int version) throws InterruptedException {
        Thread.sleep(5000);
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, version);
            if (slavePortfolio.getVersion() != version) {
                ;
            }
        }
    }

    public Instant build(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public Date buildDate(Timestamp timestamp) {
        return Date.from(build(timestamp));
    }
}
