package stpTrackingSlave.handleSynchronizeCommand.CreateSlaveOrder;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
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
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.swagger.MD.api.OrdersApi;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_DELAY_COMMAND;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("CreateSlaveOrder - Выставление заявки")
@Feature("TAP-6849")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleSynchronizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class CreateSlaveOrderErrorTest {

    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService receiverBytes;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
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
    Subscription subscription;
    SlaveOrder2 slaveOrder2;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Contract contract;
    String SIEBEL_ID_MASTER = "5-AJ7L9FNI";
    UUID strategyId;
    long subscriptionId;
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


    @SneakyThrows
    @Test
    @AllureId("701280")
    @DisplayName("C701280.CreateSlaveOrder.Выставление заявки.Биржа не работает")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C701280() {
        String SIEBEL_ID_SLAVE = "5-1YWVDYEZI";
        contractIdSlave = "2047111824";
        String ticker = "BANEP";
        String tradingClearingAccount = "L01+00000F00";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "2", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "26551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "27000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "7", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
        //смотрим, сообщение, которое поймали в топике kafka tracking.delay.command
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is("RETRY_SYNCHRONIZATION"));
    }


    @SneakyThrows
    @Test
    @AllureId("712128")
    @DisplayName("C712128.CreateSlaveOrder.Выставление заявки.ExecutionReportStatus ='Rejected'")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C712128() {
        String SIEBEL_ID_SLAVE = "4-LQB8FKN";
        contractIdSlave = "2054235441";
        String ticker = "ABBV";
        String tradingClearingAccount = "TKCBM_TCAB";
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
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
       //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        Thread.sleep(5000);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры SlaveOrder
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        //смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is("RETRY_SYNCHRONIZATION"));
    }


    @SneakyThrows
    @Test
    @AllureId("849688")
    @DisplayName("C849688.CreateSlaveOrder.Выставление заявки.Отмена заявки и повторного выставления, executionReportStatus = 'Cancelled' И lotsExecuted = 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C849688() {
        contractIdSlave = "2092721501";
        String SIEBEL_ID_SLAVE = "5-2IMV74EF5";
        String ticker = "ABBV";
        String tradingClearingAccount = "TKCBM_TCAB";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2Dao.getSlaveOrder2(contractIdSlave).getState(), notNullValue());
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры SlaveOrder
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND);
        if (messages.isEmpty()) {
            throw new RuntimeException("Нет сообщений todo");
        }
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(messages.get(0).getValue());
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is("RETRY_SYNCHRONIZATION"));
    }



    @SneakyThrows
    @Test
    @AllureId("730132")
    @DisplayName("C730132.CreateSlaveOrder.Выставление заявки.Выставление заявки.Ошибка из списка настройки fatal-error-codes")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C730132() {
        String SIEBEL_ID_SLAVE = "1-3L0X4M1";
        contractIdSlave = "2065560563";
        String ticker = "ABBV";
        String tradingClearingAccount = "TKCBM_TCAB";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        Thread.sleep(5000);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam (event);
        checkContractParam (contractIdSlave);
    }



    @SneakyThrows
    @Test
    @AllureId("851304")
    @DisplayName("C851304.CreateSlaveOrder.Выставление заявки.Ошибка из списка настройки reject-error-codes")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C851304() {
        String SIEBEL_ID_SLAVE = "4-LQB8FKN";
        contractIdSlave = "2054235441";
        String ticker = "ABBV";
        String tradingClearingAccount = "TKCBM_TCAB";
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
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);

//        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры SlaveOrder
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is("RETRY_SYNCHRONIZATION"));
    }





/////////***методы для работы тестов**************************************************************************
    //проверяем, парамерты message события в топике tracking.event
    void checkEventParam (Tracking.Event event) {
        assertThat("ID события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID договора не равен", event.getContract().getState().toString(), is("TRACKED"));
        assertThat("ID стратегии не равен", (event.getContract().getBlocked()), is(true));
    }
    //проверяем запись по контракту в табл. contract
    void checkContractParam (String contractIdSlave)  {
        contract = contractService.getContract(contractIdSlave);
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("tracked"));
        assertThat("статус клиента не равно", (contract.getBlocked()), is(true));
    }




}
