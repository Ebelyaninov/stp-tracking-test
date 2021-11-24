package stpTrackingSlave.handleSynchronizeCommand.CreateSlaveOrder;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
//import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
//import ru.tinkoff.invest.sdet.kafka.protobuf.sender.KafkaProtobufCustomSender;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


@Slf4j
@Epic("CreateSlaveOrder - Выставление заявки")
@Feature("TAP-6849")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class CreateSlaveOrderTest {

    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    MasterSignalDao masterSignalDao;
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
    Client clientSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave = "2056750892";
    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";
    public String tickerUSD = "USDRUB";
    public String tradingClearingAccountUSD = "MB9885503216";
    String classCodeUSD = "EES_CETS";
    String tickerSBER = "SBER";
    String tradingClearingAccountSBER = "L01+00002F00";

    String tickerTECH = "TUSD";
    String tradingClearingAccountTECH = "L01+00002F00";
    String classCodeTECH = "TQTD";

    BigDecimal lot = new BigDecimal("1");
    String SIEBEL_ID_MASTER = "5-AJ7L9FNI";
    String SIEBEL_ID_SLAVE = "5-23LMDXSIW";
    UUID strategyId;
    long subscriptionId;
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
                steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("668233")
    @DisplayName("C668233.CreateSlaveOrder.Выставление заявки.Action = 'Buy'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C668233() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        //выполняем расчеты
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
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(ticker, "ask", tradingClearingAccount));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        Thread.sleep(5000);
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "0", classCode, priceOrder, lots, ticker, tradingClearingAccount);
    }


    @SneakyThrows
    @Test
    @AllureId("705781")
    @DisplayName("C705781.CreateSlaveOrder.Выставление заявки.Action = 'Sell'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C705781() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "6", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        //выполняем расчеты
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
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
//        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "bid"));
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "bid", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceBid.subtract(price.multiply(new BigDecimal("0.002"))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01")));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "1", classCode, priceOrder, lots, ticker, tradingClearingAccount);
    }


    @SneakyThrows
    @Test
    @AllureId("1501937")
    @DisplayName("C1501937.CreateSlaveOrder.Выставление заявки.Action = 'Buy' для валютной позиции")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1501937() {
        String SIEBEL_ID_SLAVE= "5-DOZILMHV";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerUSD, tradingClearingAccountUSD,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, positionList);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        //выполняем расчеты
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
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(tickerUSD, "ask", tradingClearingAccountUSD));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(1, "1", "0", classCodeUSD, priceAsk, lots, tickerUSD, tradingClearingAccountUSD);
    }



    @SneakyThrows
    @Test
    @AllureId("1501989")
    @DisplayName("C1501989.CreateSlaveOrder.Выставление заявки.Action = 'Sell' для валютной позиции")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1501989() {
        String SIEBEL_ID_SLAVE= "5-DOZILMHV";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER,
            "10", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerUSD, tradingClearingAccountUSD,
            "39", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(tickerUSD, "bid", tradingClearingAccountUSD));
                //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerUSD))
            .collect(Collectors.toList());
       BigDecimal quantityDiff = position.get(0).getQuantityDiff();
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "1", classCodeUSD, priceBid, lots, tickerUSD, tradingClearingAccountUSD);
    }



    @SneakyThrows
    @Test
    @AllureId("878566")
    @DisplayName("C878566.CreateSlaveOrder.Выставление заявки.Action = 'Sell'.Продажа позиции, которая не участвует в автоследовании")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C878566() {
        String SIEBEL_ID_SLAVE= "5-DOZILMHV";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerTECH, tradingClearingAccountTECH,
            "100", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(tickerTECH, "bid", tradingClearingAccountTECH));
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerTECH))
            .collect(Collectors.toList());
        BigDecimal quantityDiff = position.get(0).getQuantityDiff();
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "1", classCodeTECH, priceBid, lots, tickerTECH, tradingClearingAccountTECH);
    }




    /////////***методы для работы тестов**************************************************************************
    void checkParamSlaveOrder(int version, String attemptsCount, String action, String classCode,
                              BigDecimal price, BigDecimal lots, String ticker, String tradingClearingAccount) {
        assertThat("Version портфеля slave не равно", slaveOrder.getVersion(), is(version));
        assertThat("AttemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is(attemptsCount));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("ClassCode не равно", slaveOrder.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder.getIdempotencyKey(), is(notNullValue()));
        assertThat("Price не равно", slaveOrder.getPrice(), is(price));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder.getCreateAt(), is(notNullValue()));
    }
}
