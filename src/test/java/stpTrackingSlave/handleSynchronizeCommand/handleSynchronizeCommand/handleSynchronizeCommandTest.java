package stpTrackingSlave.handleSynchronizeCommand.handleSynchronizeCommand;

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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Slf4j
@Epic("handleSynchronizeCommand - Обработка команд на повторную синхронизацию")
@Feature("TAP-6843")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleSynchronizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpInstrument.class
})
public class handleSynchronizeCommandTest {
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
    SlaveOrder2Dao slaveOrder2Dao;
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
    @Autowired
    StpInstrument instrument;


    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    Optional<SlaveOrder2> slaveOrder2;
    Client clientSlave;
    String contractIdMaster;
    Subscription subscription;
    String contractIdSlave = "2050306204";
    UUID strategyId;
    long subscriptionId;
    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    String SIEBEL_ID_SLAVE = "5-TJLPVJAJ";
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
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
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

    //ToDO операцию отключили
//    @SneakyThrows
//    @Test
//    @AllureId("739018")
//    @DisplayName("C739018.SynchronizePositionResolver.Выбор позиции.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C739018() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
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
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку для  slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для slave в cassandra c поизицией
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
//            new BigDecimal("-0.0211"), new BigDecimal("-2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        // добавляем запись о выставлении заявки по lave
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
//            1, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
//            (byte) 0, ticker, tradingClearingAccount, null);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        //получаем портфель мастера
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
//        //получаем портфель slave
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
//        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
//        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
//        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
//        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
//        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
//        checkSlavePortfolioParameters(2, 3, "7000.0");
//        checkPositionParameters(0, ticker, tradingClearingAccount, "7", price, slavePositionRate, rateDiff,
//            quantityDiff);
//        // рассчитываем значение
//        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
//        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "bid", SIEBEL_ID_SLAVE));
//        BigDecimal priceOrder = priceBid.subtract(priceBid.multiply(new BigDecimal("0.002")))
//            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
//            .multiply(new BigDecimal("0.01"));
//        //проверяем, что выставилась новая заявка
//        await().atMost(TEN_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")), notNullValue());
//        checkOrderParameters(2, "1", "2", lot, lots, priceOrder, ticker, tradingClearingAccount,
//            classCode);
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("739019")
//    @DisplayName("C739019.HandleRetrySynchronizationCommand.Портфель синхронизируется: lots > 0.Slave_portfolio_position.quantity_diff > 0")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C739019() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
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
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку для  slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для slave
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
//            new BigDecimal("0.0319"), new BigDecimal("2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //создаем запись о выставлении заявки
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
//            0, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
//            (byte) 0, ticker, tradingClearingAccount, null);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        //получаем портфель мастера
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
//        //получаем портфель slave
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
//        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
//        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
//        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
//        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
//        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
//        checkSlavePortfolioParameters(2, 3, "7000.0");
//        checkPositionParameters(0, ticker, tradingClearingAccount, "3", price, slavePositionRate, rateDiff,
//            quantityDiff);
//        // рассчитываем значение
//        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
//        BigDecimal lotsMax = slavePortfolio.getBaseMoneyPosition().getQuantity()
//            .divide(slavePortfolio.getPositions().get(0).getPrice()
//                .add((slavePortfolio.getPositions().get(0).getPrice().multiply(new BigDecimal("0.002")))
//                    .multiply(lot)), 0, BigDecimal.ROUND_HALF_UP);
//        BigDecimal lotsNew = BigDecimal.ZERO;
//        if (lotsMax.compareTo(lots) < 0) {
//            lotsNew = lotsMax;
//        }
//        if (lotsMax.compareTo(lots) > 0) {
//            lotsNew = lots;
//        }
//        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
//        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
//            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
//            .multiply(new BigDecimal("0.01"));
//        //проверяем, что выставилась новая заявка
//        await().atMost(TEN_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")), notNullValue());
//        checkOrderParameters(2, "0", "2", lot, lotsNew, priceOrder, ticker, tradingClearingAccount,
//            classCode);
//    }
//
//    @SneakyThrows
//    @Test
//    @AllureId("739020")
//    @DisplayName("C739020.HandleRetrySynchronizationCommand.Портфель синхронизируется: lots = 0.Выставлять повторную заявку не нужно")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C739020() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
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
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
////        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку для slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        // создаем портфель для slave с позицией
//        String baseMoneySl = "6551.1";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0717"),
//            new BigDecimal("0.0045"), new BigDecimal("2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //создаем запись о выставленной заявке
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
//            0, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
//            (byte) 0, ticker, tradingClearingAccount, null);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        //получаем портфель мастера
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
//        //получаем портфель slave
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
//        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
//        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
//        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
//        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
//        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
//        checkSlavePortfolioParameters(2, 3, "6551.1");
//        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
//            quantityDiff);
//        // рассчитываем значение лотов
//        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
//        assertThat("Количество лотов не равно", lots.toString(), is("0"));
//        //проверяем, что повторно зявка не выставилась
//        await().atMost(TEN_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1")), notNullValue());
//        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(2));
//        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
//        assertThat("attemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
//        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity().toString(), is("2"));
//        assertThat("ticker бумаги не равен", slaveOrder.getPrice(), is(new BigDecimal("108.11")));
//        assertThat("price бумаги не равен", slaveOrder.getTicker(), is(ticker));
//        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
//        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
//        assertThat("Version заявки не равно", slaveOrder.getState().toString(), is("1"));
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("738183")
//    @DisplayName("C738183.HandleRetrySynchronizationCommand.Портфель не синхронизируется")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C738183() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
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
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для slave c позицией
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
//            new BigDecimal("0.0319"), new BigDecimal("2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        //получаем портфель мастера
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
//        //получаем портфель slave
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
////        BigDecimal price = slavePortfolio.getPositions().get(0).getPrice();
//        BigDecimal price = new BigDecimal(steps. getPriceFromExchangePositionPriceCacheWithSiebel(ticker,tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
//        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
//        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
//        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
//        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
//        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
//        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
//        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
//        checkSlavePortfolioParameters(2, 3, "7000.0");
//        checkPositionParameters(0, ticker, tradingClearingAccount, "3", price, slavePositionRate, rateDiff,
//            quantityDiff);
//        // рассчитываем значение
//        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
//        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "ask", SIEBEL_ID_SLAVE));
//        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
//            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
//            .multiply(new BigDecimal("0.01"));
//        //проверяем, что выставилась заявка
//        await().atMost(TEN_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1")), notNullValue());
//        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(2));
//        checkOrderParameters(2, "0", "1", lot, lots, priceOrder, ticker, tradingClearingAccount,
//            classCode);
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("1508130")
//    @DisplayName("C1508130. Портфель синхронизируется - выставляем ту же заявку. Slave_order.state Is Null. Action = sell")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C1508130() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        BigDecimal lot = new BigDecimal("1");
//        BigDecimal priceOrder = new BigDecimal("11.11");
//        BigDecimal orderQty = new BigDecimal("22");
//        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
//        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку для  slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для slave в cassandra c поизицией
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
//            new BigDecimal("-0.0211"), new BigDecimal("-2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        // добавляем запись о выставлении заявки по lave
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
//            1, classCode, orderKey, priceOrder, orderQty,
//            null, ticker, tradingClearingAccount, BigDecimal.valueOf(0));
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        // рассчитываем значение
//        BigDecimal lots = orderQty.divide(lot);
//        //проверяем, что выставилась новая заявка
//        await().atMost(TEN_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")), notNullValue());
//        checkOrderParameters(2, "1", "2", lot, lots, priceOrder, ticker, tradingClearingAccount,
//            classCode);
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("1508129")
//    @DisplayName("C1508129. Портфель синхронизируется - выставляем ту же заявку. Slave_order.state Is Null. Action = buy")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C1508129() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        BigDecimal lot = new BigDecimal("1");
//        BigDecimal orderQty = new BigDecimal("11");
//        BigDecimal priceOrder = new BigDecimal("11.11");
//        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку для  slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для slave
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
//            new BigDecimal("0.0319"), new BigDecimal("2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //создаем запись о выставлении заявки
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
//            0, classCode, orderKey, priceOrder, orderQty,
//            null, ticker, tradingClearingAccount, new BigDecimal("0"));
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        BigDecimal lots = orderQty.divide(lot);
//        //проверяем, что выставилась новая заявка
//        await().atMost(TEN_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")), notNullValue());
//        checkOrderParameters(2, "0", "2", lot, lots, priceOrder, ticker, tradingClearingAccount,
//            classCode);
//    }
//
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("1510128")
//    @DisplayName("C1510128. Портфель синхронизируется - выставляем ту же заявку. И slave_order.filled_quantity != 0")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C1510128() {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        BigDecimal lot = new BigDecimal("1");
//        BigDecimal orderQty = new BigDecimal("33");
//        BigDecimal priceOrder = new BigDecimal("11.11");
//        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку для  slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для slave
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
//            new BigDecimal("0.0319"), new BigDecimal("2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //создаем запись о выставлении заявки
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
//            0, classCode, orderKey, priceOrder, orderQty,
//            null, ticker, tradingClearingAccount, new BigDecimal("1"));
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
//        //проверяем, что не выставилась новая заявка
//        assertThat("Нашли новую запись", slaveOrderDao.findSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("2")).toString(), is("Optional.empty"));
//    }


    @SneakyThrows
    @Test
    @AllureId("1575130")
    @DisplayName("С1575130. Запись не найдена - портфель не синхронизируется")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1575130() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave).stream()
                .filter(getSlaveOrder -> getSlaveOrder.getAttemptsCount().equals(1))
                .collect(Collectors.toList()).size(), is(1));
        //проверяем, что  выставилась новая заявка
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);
        assertThat("action != 0", getSlaveOrder.get().getAction().toString(), is("0"));
        assertThat("getAttemptsCount != 1", getSlaveOrder.get().getAttemptsCount().toString(), is("1"));
        assertThat("getVersion != 2", getSlaveOrder.get().getVersion(), is(2));
        assertThat("getClassCode != " + instrument.classCodeAAPL, getSlaveOrder.get().getClassCode(), is(instrument.classCodeAAPL));
        assertThat("getTicker != " + instrument.tickerAAPL, getSlaveOrder.get().getTicker(), is(instrument.tickerAAPL));
        assertThat("getTradingClearingAccount != " + instrument.tradingClearingAccountAAPL, getSlaveOrder.get().getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
    }


    @SneakyThrows
    @Test
    @AllureId("1575132")
    @DisplayName("C1575132. Запись найдена в slave_order И slave_order.state = 1")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1575132() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        BigDecimal orderQty = new BigDecimal("33");
        BigDecimal priceOrder = new BigDecimal("11.11");
        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc, strategyId, 1, 1,
            0, instrument.classCodeAAPL, 3,null, orderKey, orderKey, priceOrder, orderQty,
            (byte) 1, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave).stream()
                .filter(getSlaveOrder -> getSlaveOrder.getAttemptsCount().equals(1))
                .collect(Collectors.toList()).size(), is(1));
        //проверяем, что  выставилась новая заявка
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);

        assertThat("action != 0", getSlaveOrder.get().getAction().toString(), is("0"));
        assertThat("getAttemptsCount != 1", getSlaveOrder.get().getAttemptsCount().toString(), is("1"));
        assertThat("getVersion != 2", getSlaveOrder.get().getVersion(), is(2));
        assertThat("getClassCode != " + instrument.classCodeAAPL, getSlaveOrder.get().getClassCode(), is(instrument.classCodeAAPL));
        assertThat("getIdempotencyKey получили старый", getSlaveOrder.get().getIdempotencyKey().equals(orderKey), is(false));
        assertThat("getTicker != " + instrument.tickerAAPL, getSlaveOrder.get().getTicker(), is(instrument.tickerAAPL));
        assertThat("getTradingClearingAccount != " + instrument.tradingClearingAccountAAPL, getSlaveOrder.get().getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
    }


    @SneakyThrows
    @Test
    @AllureId("1575133")
    @DisplayName("C1575133. Запись найдена в slave_order И slave_order.state = 0(отклонена)")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1575133() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        BigDecimal orderQty = new BigDecimal("33");
        BigDecimal priceOrder = new BigDecimal("11.11");
        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), orderKey, priceOrder, orderQty,
            (byte) 0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave).stream()
                .filter(getSlaveOrder -> getSlaveOrder.getAttemptsCount().equals(2))
                .collect(Collectors.toList()).size(), is(1));
        //проверяем, что  выставилась новая заявка
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);

        assertThat("action != 0", getSlaveOrder.get().getAction().toString(), is("0"));
        assertThat("getQuantity != 2", getSlaveOrder.get().getQuantity().toString(), is("2"));
        assertThat("getAttemptsCount != 2", getSlaveOrder.get().getAttemptsCount().toString(), is("2"));
        assertThat("getVersion != 2", getSlaveOrder.get().getVersion(), is(2));
        assertThat("getClassCode != " + instrument.classCodeAAPL, getSlaveOrder.get().getClassCode(), is(instrument.classCodeAAPL));
        assertThat("getIdempotencyKey получили старый", getSlaveOrder.get().getIdempotencyKey().equals(orderKey), is(false));
        assertThat("getTicker != " + instrument.tickerAAPL, getSlaveOrder.get().getTicker(), is(instrument.tickerAAPL));
        assertThat("getTradingClearingAccount != " + instrument.tradingClearingAccountAAPL, getSlaveOrder.get().getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
    }


    private static Stream<Arguments> provideOperationAndActionAndState() {
        return Stream.of(
            Arguments.of(Tracking.PortfolioCommand.Operation.SYNCHRONIZE, 0, null),
            Arguments.of(Tracking.PortfolioCommand.Operation.SYNCHRONIZE, 1, null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideOperationAndActionAndState")
    @AllureId("1575128")
    @DisplayName("C1575128. Портфель синхронизируется. Нашли запись в slave_order.state IS null - выставляем ту же заявку (RETRY_SYNCHRONIZATION)")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1575128(Tracking.PortfolioCommand.Operation command, int action, Byte state) {
        //Tracking.PortfolioCommand.Operation command = Tracking.PortfolioCommand.Operation.RETRY_SYNCHRONIZATION;
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        BigDecimal orderQty = new BigDecimal("100");
        BigDecimal priceOrder = new BigDecimal("11.11");
        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc, strategyId, 2, 1,
            action, instrument.classCodeAAPL,33, new BigDecimal("0"), orderKey, orderKey, priceOrder, orderQty,
            state, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);

        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave).stream()
                .filter(getSlaveOrder -> getSlaveOrder.getAttemptsCount().equals(2))
                .collect(Collectors.toList()).size(), is(1));
        //проверяем, что  выставилась новая заявка
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);

        assertThat("action != " + action, getSlaveOrder.get().getAction().toString(), is(String.valueOf(action)));
        assertThat("getQuantity != " + orderQty, getSlaveOrder.get().getQuantity().toString(), is(orderQty.toString()));
        assertThat("getPrice != " + priceOrder, getSlaveOrder.get().getPrice().toString(), is(priceOrder.toString()));
        assertThat("compared_to_master_version  != выставленой заявке 33", getSlaveOrder.get().getComparedToMasterVersion().toString(), is("33"));
        assertThat("getAttemptsCount != 2", getSlaveOrder.get().getAttemptsCount().toString(), is("2"));
        assertThat("getIdempotencyKey != " + orderKey, getSlaveOrder.get().getIdempotencyKey(), is(orderKey));
        assertThat("getVersion != 2", getSlaveOrder.get().getVersion(), is(2));
        assertThat("getClassCode != " + instrument.classCodeAAPL, getSlaveOrder.get().getClassCode(), is(instrument.classCodeAAPL));
        assertThat("getTicker != " + instrument.tickerAAPL, getSlaveOrder.get().getTicker(), is(instrument.tickerAAPL));
        assertThat("getTradingClearingAccount != " + instrument.tradingClearingAccountAAPL, getSlaveOrder.get().getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
    }


    @SneakyThrows
    @Test
    @AllureId("1773720")
    @DisplayName("C1773720. Нашли запись в таблице slave_order_2 и slave_order_2.state = 2 -> выставляем ту же заявку.")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1773720() {
        //Tracking.PortfolioCommand.Operation command = Tracking.PortfolioCommand.Operation.RETRY_SYNCHRONIZATION;
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        BigDecimal orderQty = new BigDecimal("100");
        BigDecimal priceOrder = new BigDecimal("11.11");
        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc, strategyId, 2, 1,
            1, instrument.classCodeAAPL,33, new BigDecimal("0"), orderKey, orderKey, priceOrder, orderQty,
            (byte) 2, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);

        await().pollDelay(Duration.ofSeconds(2)).atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave).stream()
                .filter(getSlaveOrder -> getSlaveOrder.getAttemptsCount().equals(1))
                .collect(Collectors.toList()).size(), is(1));
        //проверяем, что завершили операцию и невыставили новую заявку
        List<SlaveOrder2> getDataFromSlaveOrder = slaveOrder2Dao.getAllSlaveOrder2ByContract(contractIdSlave);
        assertThat("Выставили новую заявку", getDataFromSlaveOrder.size(), is(1));
    }


    @SneakyThrows
    @Test
    @AllureId("1499847")
    @DisplayName("C1499847. Ограничиваем выставление заявки настройкой order-execute.max-attempts-count")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1499847() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем, кол-во попыток на выставление заявки = 126
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);

        if (getSlaveOrder.get().getAttemptsCount() < 123) {
            OffsetDateTime dateOfSlaveOrder = OffsetDateTime.ofInstant(getSlaveOrder.get().getCreateAt().toInstant(), ZoneId.of("UTC")).plusSeconds(2);
            slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, dateOfSlaveOrder, strategyId, 2, 123,
                0, instrument.classCodeAAPL,3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), getSlaveOrder.get().getPrice(), getSlaveOrder.get().getQuantity(),
                (byte) 0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
            getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);
            Thread.sleep(30000);
        }
        getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);
        Integer i = 0;
        Integer attemptsCount = getSlaveOrder.get().getAttemptsCount();
        while (attemptsCount.compareTo(126) > 0) {
            getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);
            attemptsCount = getSlaveOrder.get().getAttemptsCount();
            i++;
            if (i == 4) {
                attemptsCount = 126;
                Thread.sleep(200);
                break;
            }
            Thread.sleep(30000);
        }

        assertThat("Контракт не заблокировали", contractService.getContract(contractIdSlave).getBlocked(), is(true));
        //Проверяем последнию запись в таблице slave_order_2
        getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);
        assertThat("Контракт не заблокировали", getSlaveOrder.get().getAttemptsCount(), is(126));
    }


    @SneakyThrows
    @Test
    @AllureId("1652853")
    @DisplayName("С1652853. Новое рассчитанное значение attempts_count > значения настройки order-execute.max-attempts-count")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1652853() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        BigDecimal orderQty = new BigDecimal("33");
        BigDecimal priceOrder = new BigDecimal("11.11");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.0319"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc.minusSeconds(90), strategyId, 2, 124,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), priceOrder, orderQty,
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc.minusSeconds(60), strategyId, 2, 125,
            0, instrument.classCodeAAPL,3,  new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), priceOrder, orderQty,
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc.minusSeconds(30), strategyId, 2, 126,
            1, instrument.classCodeAAPL,3,  new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), priceOrder, orderQty,
            (byte) 0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(400)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "bid", SIEBEL_ID_SLAVE));
        priceOrder = priceBid.subtract(priceBid.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем, что выставилась новая заявка
        slaveOrder2 = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);
        //Проверяем добавление новой заявки с attemptsCount = 1
        checkOrderParameters(2, "1", "1", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.classCodeAAPL);
    }

    @NullAndEmptySource
    private Stream<Arguments> provideActionTickerTradingClearingAccountAndAttemptsCount() {
        return Stream.of(
            //Логика с Портфель синхронизируется - выставляем ту же заявку,
            //Arguments.of(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, null, null),
            Arguments.of(1, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 1, "0", 3),
            Arguments.of(0, instrument.tickerABBV, instrument.tradingClearingAccountABBV, 1, "1", 3),
            Arguments.of(0, instrument.tickerAAPL, instrument.tradingClearingAccountNOK, 1, "1", 3)
        );
    }


    @SneakyThrows
    @ParameterizedTest(name = "provideActionTickerTradingClearingAccountAndAttemptsCount")
    @MethodSource("provideActionTickerTradingClearingAccountAndAttemptsCount")
    @AllureId("1656925")
    @DisplayName("C1656925.Портфель не синхронизируется. Работа с параметром attempts_count в случае если параметры ticker,trading_clearing_account и action " +
        "найденной неисполненной заявки в slave_order_2 совпали/не совпали с ticker,trading_clearing_account и action найденной позиции на этапе Выбора позиции для синхронизации")
    @Subfeature("Успешные сценарии")
    @Description("handleSynchronizeCommand - Обработка команд на синхронизацию SYNCHRONIZE")
    void C1656925(int actionSlave, String tickerSlave, String tradingClearingAccountSlave, int attemptsCount, Byte state, Integer comparedToMasterVersion) {
        Tracking.PortfolioCommand.Operation command = Tracking.PortfolioCommand.Operation.SYNCHRONIZE;
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal orderQty = new BigDecimal("33");
        BigDecimal priceOrder = new BigDecimal("11.11");
        UUID orderKey = UUID.fromString("4798ae0e-debb-4e7d-8991-2a4e735740c6");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.fromString("6149677a-b1fd-401b-9611-80913dfe2621");
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "20", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        List<MasterPortfolio.Position> masterPos1 = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "12", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos1);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "6951.1";
        steps.createSlavePortfolioWithoutPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date);
        //создаем запись о выставлении заявки
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, utc, strategyId, 2, 1,
            actionSlave, instrument.classCodeAAPL, 3, new BigDecimal("0"), orderKey, orderKey, priceOrder, orderQty,
            state, tickerSlave, tradingClearingAccountSlave);

        //отправляем команду на  повторную синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);


        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave).stream()
                .filter(getSlaveOrder -> getSlaveOrder.getAttemptsCount().equals(attemptsCount))
                .collect(Collectors.toList()).size(), is(1));
        //проверяем, что  выставилась новая заявка
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getLatestSlaveOrder2(contractIdSlave);

        Integer expectedComparedToMasterVersion = getSlaveOrder.get().getComparedToMasterVersion();

        assertThat("getAttemptsCount != " + attemptsCount, getSlaveOrder.get().getAttemptsCount(), is(attemptsCount));
        assertThat("compared_to_master_version != " + comparedToMasterVersion, expectedComparedToMasterVersion, is(comparedToMasterVersion));

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
        assertThat("Version заявки не равно", slaveOrder2.get().getVersion(), is(version));
        assertThat("Направление заявки Action не равно", slaveOrder2.get().getAction().toString(), is(action));
        assertThat("attemptsCount не равно", slaveOrder2.get().getAttemptsCount().toString(), is(attemptsCount));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.get().getQuantity(), is(lots.multiply(lot)));
        assertThat("price бумаги не равен", slaveOrder2.get().getPrice(), is(priceOrder));
        assertThat("ticker бумаги не равен", slaveOrder2.get().getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder2.get().getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.get().getTradingClearingAccount(), is(tradingClearingAccount));
    }

}
