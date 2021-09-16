package stpTrackingSlave.handleSynchronizeCommand.handleRetrySynchronizationCommand;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Repeat;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
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
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("handleSynchronizeCommand - Обработка команд на повторную синхронизацию")
@Feature("TAP-6843")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class HandleRetrySynchronizationCommandTest {
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService kafkaStringSender;
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
    Client clientSlave;
    String contractIdMaster;
    Subscription subscription;
    String contractIdSlave = "2050306204";
    UUID strategyId;
    long subscriptionId;
    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    //    String SIEBEL_ID_MASTER = "4-1V1UVPX8";
    String SIEBEL_ID_SLAVE = "5-1NIRKRJIH";
    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";

    public String value;





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
    @AllureId("739018")
    @DisplayName("C739018.SynchronizePositionResolver.Выбор позиции.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C739018() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
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
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c поизицией
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
            new BigDecimal("-0.0211"), new BigDecimal("-2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        // добавляем запись о выставлении заявки по lave
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            1, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
            (byte) 0, ticker, tradingClearingAccount);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "7", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "bid", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceBid.subtract(priceBid.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем, что выставилась новая заявка
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")), notNullValue());
        checkOrderParameters(2, "1", "2", lot, lots, priceOrder, ticker, tradingClearingAccount,
            classCode);
    }


    @SneakyThrows
    @Test
    @AllureId("739019")
    @DisplayName("C739019.HandleRetrySynchronizationCommand.Портфель синхронизируется: lots > 0.Slave_portfolio_position.quantity_diff > 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C739019() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        steps.createDataToMarketData(ticker, classCode, "108.09", "107.79", "107.72");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            0, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "3", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .divide(slavePortfolio.getPositions().get(0).getPrice()
                .add((slavePortfolio.getPositions().get(0).getPrice().multiply(new BigDecimal("0.002")))
                    .multiply(lot)), 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsNew = BigDecimal.ZERO;
        if (lotsMax.compareTo(lots) < 0) {
            lotsNew = lotsMax;
        }
        if (lotsMax.compareTo(lots) > 0) {
            lotsNew = lots;
        }
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем, что выставилась новая заявка
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")), notNullValue());
        checkOrderParameters(2, "0", "2", lot, lotsNew, priceOrder, ticker, tradingClearingAccount,
            classCode);
    }

    @SneakyThrows
    @Test
    @AllureId("739020")
    @DisplayName("C739020.HandleRetrySynchronizationCommand.Портфель синхронизируется: lots = 0.Выставлять повторную заявку не нужно")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C739020() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
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
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель для slave с позицией
        String baseMoneySl = "6551.1";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0717"),
            new BigDecimal("0.0045"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставленной заявке
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            0, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "6551.1");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение лотов
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        assertThat("Количество лотов не равно", lots.toString(), is("0"));
        //проверяем, что повторно зявка не выставилась
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1")), notNullValue());
        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(2));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("attemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity().toString(), is("2"));
        assertThat("ticker бумаги не равен", slaveOrder.getPrice(), is(new BigDecimal("108.11")));
        assertThat("price бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Version заявки не равно", slaveOrder.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("738183")
    @DisplayName("C738183.HandleRetrySynchronizationCommand.Портфель не синхронизируется")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C738183() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
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
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave c позицией
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        BigDecimal price = slavePortfolio.getPositions().get(0).getPrice();
        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "3", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем, что выставилась заявка
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1")), notNullValue());
        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(2));
        checkOrderParameters(2, "0", "1", lot, lots, priceOrder, ticker, tradingClearingAccount,
            classCode);
    }




//методы для тестов*************************************************************************************

    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        Integer synchronizedToMasterVersion, BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal masterPositionRate, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("SynchronizedToMasterVersion позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getSynchronizedToMasterVersion(), is(synchronizedToMasterVersion));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(masterPositionRate));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }


    public void checkSlavePortfolioParameters(int version, int comparedToMasterVersion, String baseMoney) {
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(version));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(comparedToMasterVersion));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney));
    }


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal rateDiff, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }


    public void checkOrderParameters(int version, String action, String attemptsCount, BigDecimal lot, BigDecimal lots,
                                     BigDecimal priceOrder, String ticker, String tradingClearingAccount,
                                     String classCode) {
        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(version));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("attemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is(attemptsCount));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("price бумаги не равен", slaveOrder.getPrice(), is(priceOrder));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
    }

}
