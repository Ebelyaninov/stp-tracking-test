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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
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
import java.time.*;
import java.util.*;

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

public class HandleEnableSynchronizationCommandErrorTest {

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
    UUID idempotencyKey;
    UUID id;

    // String siebelIdMaster = "5-23AZ65JU2";
    // String siebelIdSlave = "4-LQB8FKN";
    String siebelIdMaster = "1-9X6NHTJ";
    String siebelIdSlave = "5-6UTY74RE";

    Subscription subscription;
    UUID strategyId;
    String description;
    String title;

    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";

    String ticker1 = "ALFAperp";
    String tradingClearingAccount1 = "TKCBM_TCAB";

    String ticker2 = "FB";
    String tradingClearingAccount2 = "TKCBM_TCAB";

    String ticker3 = "BCR";
    String tradingClearingAccount3 = "TKCBM_TCAB";

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
    @AllureId("1388129")
    @DisplayName("1388129. Запись в strategyCache по ключу strategyId = contract.strategy_id не найдена")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1388129() {
        steps.createClientWithContract(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            null, ContractState.tracked, strategyId);
        // создаем портфель slave с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //Вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(10));
        Pair<String, byte[]> messageSlave = messages.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(messageSlave.getValue());
        //Вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages1 = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(30));
        Pair<String, byte[]> messageEvent = messages1.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(messageEvent.getValue());
        //получаем портфель slave
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //Проверяем, данные в сообщении из tracking.contract.event
        checkEvent(event, contractIdSlave, "UPDATED", "UNTRACKED", true);
        //Проверяем портфель slave
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(null));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(null));
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1388136")
    @DisplayName("1388136 Запись в кэше subscriptionCache по подписке не найдена")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1388136() {
        idempotencyKey = UUID.randomUUID();
        id = UUID.randomUUID();
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
        subscriptionService.deleteSubscription(subscription);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //добавляем запись в таблицу slave_order_2
        slaveOrderDao.insertIntoSlaveOrder2(contractIdSlave, OffsetDateTime.now(), strategyId,
            2, 1, 0, "SPBMX", new BigDecimal(1), idempotencyKey,
            id, new BigDecimal(107), new BigDecimal(1), (byte) 1, ticker, tradingClearingAccount);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandEnableSynchronization(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .sorted(Collections.reverseOrder())
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
    }



    @SneakyThrows
    @Test
    @AllureId("1378814")
    @DisplayName("1378814 Запись по портфелю не найдена в таблице master_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1378814() {
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
        // создаем портфель slave с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //Вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
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
        //получаем портфель slave
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //Смотрим, сообщение, которое поймали в топике tracking.contract.event
        List<Pair<String, byte[]>> messages1 = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(30));
        Pair<String, byte[]> messageEvent = messages1.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(messageEvent.getValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //Проверяем, данные в сообщении из tracking.contract.event
        checkEvent(event, contractIdSlave, "UPDATED", "TRACKED", true);
        //Проверяем портфель slave
        assertThat("sell_enabled не равен", slavePortfolio.getPositions().get(0).getSellEnabled(), is(nullValue()));
        assertThat("buy_enabled не равен", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(nullValue()));
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));

    }


    @SneakyThrows
    @Test
    @AllureId("1378788")
    @DisplayName("1378788 Запись по портфелю не найдена в таблице slave_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1378788() {
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
        //Вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
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
        //Смотрим, сообщение, которое поймали в топике tracking.contract.event
        List<Pair<String, byte[]>> messages1 = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(30));
        Pair<String, byte[]> messageEvent = messages1.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(messageEvent.getValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //Проверяем, данные в сообщении из tracking.contract.event
        checkEvent(event, contractIdSlave, "UPDATED", "TRACKED", true);
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));

    }


    @SneakyThrows
    @Test
    @AllureId("1378818")
    @DisplayName("1378818 Значение договора blocked = true из команды на включение синхронизации в обе стороны")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1378818() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем портфель ведущего с позицией в кассандре
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
        //создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //Блокируем контракт slave
       // adminSteps.BlockContract(contractIdSlave);
        //Вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
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
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));

    }



    @SneakyThrows
    @Test
    @AllureId("1578634")
    @DisplayName("1578634 Определяем, находится ли портфель slave'а в процессе синхронизации. order.state = null")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1578634() {
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
            id, new BigDecimal(500), new BigDecimal(3), null, ticker2, tradingClearingAccount2);
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
        assertThat("state заполнен", slaveOrder.getState(), is(nullValue()));
        assertThat("attempts_count не равен", slaveOrder.getAttemptsCount().intValue(), is(1));
        assertThat("quantity не равно", slaveOrder.getQuantity().intValue(), is(createListSlaveOnePos.get(0).getQuantity().intValue()));

    }


    @SneakyThrows
    @Test
    @AllureId("1378815")
    @DisplayName("1378815 Ошибка на этапе Анализа портфеля и фиксации результата по команде на включение синхронизации в обе стороны")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка команды на включение синхронизации в обе стороны")
    void C1378815() {
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker3, tradingClearingAccount3,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //Вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
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
        //Смотрим, сообщение, которое поймали в топике tracking.contract.event
        List<Pair<String, byte[]>> messages1 = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(30));
        Pair<String, byte[]> messageEvent = messages1.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(messageEvent.getValue());
        //Проверяем, данные в сообщении из tracking.slave.command
        checkEventParams(portfolioCommand, contractIdSlave, "ENABLE_SYNCHRONIZATION");
        //Проверяем, данные в сообщении из tracking.contract.event
        checkEvent(event, contractIdSlave, "UPDATED", "TRACKED", true);
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));

    }




    //методы

    //метод рандомайза для номера теста
    public static int randomNumber(int min, int max) {
        int number = min + (int) (Math.random() * max);
        return number;
    }

/*
    //метод для создания вставки заявки
    void createSlaveOrder(int minusDays, int minusHours, String contractId, UUID strategyId, int version, int attemptsCount,
                          int action, String classCode, int filledQuantity, UUID id,
                          UUID idempotencyKey, String price, String quantity, int state, String ticker, String tradingClearingAccount) {
        LocalDateTime time = LocalDateTime.now().minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        SlaveOrder2 slaveOrder = SlaveOrder2.builder()
            .contractId(contractId)
            .strategyId(strategyId)
            .version(version)
            .attemptsCount(attemptsCount)
            .action((byte) action)
            .classCode(classCode)
            .filledQuantity(new BigDecimal(filledQuantity))
            .id(id)
            .idempotencyKey(idempotencyKey)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .state((byte) 0)
            .tradingClearingAccount(tradingClearingAccount)
            .ticker(ticker)
            .createAt(convertedDatetime)
            .build();
        slaveOrder2Dao.insertSlaveOrder(slaveOrder);
    }

    //метод создает записи по заявкам в рамках одной стратегии
    void createTestDataSlaveOrder(int version, int count, int attemptsCounts, int action, String classCode, String ticker, String tradingClearingAccount) {
        idempotencyKey = UUID.randomUUID();
        for(int i=0; i<count; i++) {
            attemptsCounts = attemptsCounts + 1;
            createSlaveOrder(43, 9, contractIdSlave, strategyId, version, attemptsCounts, action, classCode, 0, idempotencyKey, "173", "1", 0, ticker, tradingClearingAccount);
        }
    }
*/

    //Проверяем параметры события
    void checkEventParams(Tracking.PortfolioCommand portfolioCommand, String contractId, String operation) {
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
        assertThat("Operation не равен", portfolioCommand.getOperation(), is(Tracking.PortfolioCommand.Operation.ENABLE_SYNCHRONIZATION));

    }

    void checkEvent(Tracking.Event event, String contractId, String action, String state, boolean blocked) {
        assertThat("ID contract не равен", event.getContract().getId(), is(contractId));
        assertThat("Action не равен", event.getAction().toString(), is(action));
        assertThat("State не равен", event.getContract().getState().toString(), is(state));
        assertThat("Blocked не равен", (event.getContract().getBlocked()), is(blocked));

    }

}
