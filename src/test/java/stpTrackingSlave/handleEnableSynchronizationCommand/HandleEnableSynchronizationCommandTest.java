package stpTrackingSlave.handleEnableSynchronizationCommand;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.support.NullValue;
import org.springframework.retry.backoff.ThreadWaitSleeper;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
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
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;


@Slf4j
@Epic("handleEnableSynchronizationCommand-Обработка команды на включение синхронизации в обе стороны")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleEnableSynchronizationCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})

public class HandleEnableSynchronizationCommandTest {

    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
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
    SlaveOrder2Dao slaveOrderDao;
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

    SlavePortfolio slavePortfolio;
    SlaveOrder2 slaveOrder;
    Client clientSlave;

    String contractIdMaster;
    String contractIdSlave;

    UUID investIdSlave;
    UUID investIdMaster;

   // String siebelIdMaster = "5-23AZ65JU2";
   // String siebelIdSlave = "4-LQB8FKN";
    String siebelIdMaster = "1-9X6NHTJ";
    String siebelIdSlave = "5-6UTY74RE";

    Subscription subscription;

    UUID strategyId;
    UUID idempotencyKey;
    UUID id;

    String description;
    String title;


    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";

    String ticker1 = "ALFAperp";
    String tradingClearingAccount1 = "TKCBM_TCAB";

    String ticker2 = "FB";
    String tradingClearingAccount2 = "TKCBM_TCAB";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }
            try {
                slaveOrderDao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }

    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
    }

    @BeforeEach
    void getStrategyData(){
        title = "Autotest" + randomNumber(0,100);
        description = "Autotest HandleEnableSynchronization";
        strategyId = UUID.randomUUID();
    }

    @SneakyThrows
    @Test
    @AllureId("1388423")
    @DisplayName("1388423. Успешная обработка команды на включение синхронизации в обе стороны для share.Sell")
    @Subfeature("Успешные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1388423() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //получаем портфель slave
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //Проверяем, данные в сообщении
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //Проверяем данные портфеля
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        //получаем выставленную заявку
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder2(contractIdSlave), notNullValue());
        //Проверяем данные заявки
        assertThat("ticker не равен", slaveOrder.getTicker(), is(ticker1));
        assertThat("tradingClearingAccount не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount1));
        assertThat("version не равна", slaveOrder.getVersion(), is(2));
        assertThat("quantity не равно", slaveOrder.getQuantity().intValue(), is(createListSlaveOnePos.get(0).getQuantity()));
    }


    @SneakyThrows
    @Test
    @AllureId("1378785")
    @DisplayName("1378785. Успешная обработка команды на включение синхронизации в обе стороны для share.Buy")
    @Subfeature("Успешные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1378785() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //получаем портфель slave
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем выставленную заявку
        Thread.sleep(10000);
        slaveOrder = slaveOrderDao.getSlaveOrder2ByStrategy(contractIdSlave, strategyId);
        //Проверяем, данные в сообщении
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //Проверяем данные портфеля
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        //Проверяем данные заявки
        assertThat("ticker не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("tradingClearingAccount не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("version не равна", slaveOrder.getVersion(), is(2));
        assertThat("quantity не равно", slaveOrder.getQuantity().intValue(), is(createListSlaveOnePos.get(0).getQuantity()));

    }


    @SneakyThrows
    @Test
    @AllureId("1578651")
    @DisplayName("1578651 Определяем, находится ли портфель slave'а в процессе синхронизации. order.state = 0")
    @Subfeature("Успешные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1578651() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker2, tradingClearingAccount2,
            "1", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "21512", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем портфель slave с позицией в кассандре
        String baseMoneySl = "4320.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker2, tradingClearingAccount2,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //добавляем запись в таблицу slave_order_2
        slaveOrderDao.insertIntoSlaveOrder2(contractIdSlave, OffsetDateTime.now(), strategyId,
            2, 1, 1, "SPBMX", new BigDecimal(1), idempotencyKey,
            id, new BigDecimal(500), new BigDecimal(3), (byte) 0, ticker2, tradingClearingAccount2);
        //Вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике tracking.slave.command
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //получаем портфель slave
        //await().atMost(TEN_SECONDS).until(() ->
        Thread.sleep(10000);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);//, notNullValue());
        //получаем выставленную заявку
        Thread.sleep(10000);
        slaveOrder = slaveOrderDao.getSlaveOrder2(contractIdSlave);
        //Проверяем данные портфеля
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        //Проверяем данные заявки
        assertThat("ticker не равен", slaveOrder.getTicker(), is(ticker2));
        assertThat("tradingClearingAccount не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount2));
        assertThat("version не равна", slaveOrder.getVersion(), is(2));
        assertThat("state не равен", slaveOrder.getState().intValue(), is(0));
        assertThat("attempts_count не равен", slaveOrder.getAttemptsCount().intValue(), is(2));
        assertThat("quantity не равно", slaveOrder.getQuantity().intValue(), is(createListSlaveOnePos.get(0).getQuantity().intValue()));

    }


    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(1),
            Arguments.of(125),
            Arguments.of(126)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("1654359")
    @DisplayName("1654359 handleEnableSynchronizationCommand. state = 0. ticker + trading_clearing_account + action из slave_order = значению ticker + trading_clearing_account + action выставляемой заявки")
    @Subfeature("Успешные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1654359(int attempts_count) {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку на стратегию
        OffsetDateTime startTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //добавляем запись в таблицу slave_order_2
        slaveOrderDao.insertIntoSlaveOrder2(contractIdSlave, OffsetDateTime.now(), strategyId,
            2, attempts_count, 1, "SPBMX", new BigDecimal(1), idempotencyKey,
            id, new BigDecimal(500), new BigDecimal(2), (byte) 0, ticker1, tradingClearingAccount1);
        //Вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике tracking.slave.command
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //получаем портфель slave
        Thread.sleep(10000);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);//, notNullValue());
        //получаем выставленную заявку
        //Thread.sleep(10000);
        slaveOrder = slaveOrderDao.getSlaveOrder2ByStrategy(contractIdSlave, strategyId);
        //Проверяем данные портфеля
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        //Проверяем данные заявки
        assertThat("ticker не равен", slaveOrder.getTicker(), is(ticker1));
        assertThat("tradingClearingAccount не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount1));
        assertThat("action не равен", slaveOrder.getAction().intValue(), is(1));
        assertThat("version не равна", slaveOrder.getVersion(), is(2));
        assertThat("state не равен", slaveOrder.getState().intValue(), is(0));
        assertThat("quantity не равно", slaveOrder.getQuantity().intValue(), is(createListSlaveOnePos.get(0).getQuantity().intValue()));
        if (attempts_count == 1) {
            assertThat("attempts_count не равен", slaveOrder.getAttemptsCount().intValue(), not(1));
        }
        if (attempts_count == 125) {
            assertThat("attempts_count не равен", slaveOrder.getAttemptsCount().intValue(), is(126));
            //проверяем, что контракт заблокирован
            assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));
        }
        else {
            assertThat("attempts_count не равен", slaveOrder.getAttemptsCount().intValue(), not(126));
        }


    }

    private static Stream<Arguments> Params() {
        return Stream.of(
            Arguments.of("AAPL", "TKCBM_TCAB", 1),
            Arguments.of("ALFAperp", "TKCBM_TCAB", 0)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("Params")
    @AllureId("1654361")
    @DisplayName("1654361 handleEnableSynchronizationCommand. state = 0. ticker + trading_clearing_account + action из slave_order != значению ticker + trading_clearing_account + action выставляемой заявки")
    @Subfeature("Успешные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1654361(String tickerA, String tradingClearingAccountA, int action) {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку на стратегию
        OffsetDateTime startTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //добавляем запись в таблицу slave_order_2
        slaveOrderDao.insertIntoSlaveOrder2(contractIdSlave, OffsetDateTime.now(), strategyId,
            2, 100, action, "SPBMX", new BigDecimal(1), idempotencyKey,
            id, new BigDecimal(500), new BigDecimal(3), (byte) 0, tickerA, tradingClearingAccountA);
        //Вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике tracking.slave.command
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //получаем портфель slave
        Thread.sleep(10000);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);//, notNullValue());
        //получаем выставленную заявку
        Thread.sleep(10000);
        slaveOrder = slaveOrderDao.getSlaveOrder2ByStrategy(contractIdSlave, strategyId);
        //Проверяем данные портфеля
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        //Проверяем данные заявки
        assertThat("ticker не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("tradingClearingAccount не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("action не равен", slaveOrder.getAction().intValue(), is(0));
        assertThat("version не равна", slaveOrder.getVersion(), is(2));
        assertThat("state не равен", slaveOrder.getState().intValue(), is(0));
        assertThat("quantity не равно", slaveOrder.getQuantity().intValue(), is(createListSlaveOnePos.get(0).getQuantity().intValue()));
        assertThat("attempts_count не равно", slaveOrder.getAttemptsCount().intValue(), is(1));
    }


    //методы

    //метод рандомайза для номера теста
    public static int randomNumber(int min, int max) {
        int number = min + (int) (Math.random() * max);
        return number;
    }

    //Проверяем параметры события
    void checkEventParams(Tracking.PortfolioCommand portfolioCommand, String contractId, String operation) {
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
        assertThat("Operation не равен", portfolioCommand.getOperation(), is(Tracking.PortfolioCommand.Operation.ENABLE_SYNCHRONIZATION));

    }

}
