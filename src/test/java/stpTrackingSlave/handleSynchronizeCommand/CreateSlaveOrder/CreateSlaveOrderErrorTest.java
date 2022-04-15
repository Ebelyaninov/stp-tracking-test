package stpTrackingSlave.handleSynchronizeCommand.CreateSlaveOrder;

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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.mocks.steps.MocksBasicSteps;
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMockSlaveDateConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMockSlave.StpMockSlaveDate;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
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
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_DELAY_COMMAND;

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
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingMockSlaveDateConfiguration.class,
    MocksBasicStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class
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
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    MocksBasicSteps mocksBasicSteps;
    @Autowired
    StpMockSlaveDate mockSlaveDate;


    Subscription subscription;
    SlaveOrder2 slaveOrder2;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Contract contract;
    String SIEBEL_ID_MASTER;
    String slaveOrder;
    String masterOrder;
    UUID strategyId;
    long subscriptionId;
    String description = "description autotest CreateSlaveOrderError";

    @BeforeAll
    void getdataFromInvestmentAccount() {

        SIEBEL_ID_MASTER = stpSiebel.siebelIdMasterAnalytics1;
        slaveOrder = stpSiebel.siebelIdSlaveOrder;
        masterOrder = stpSiebel.siebelIdMasterOrder;
    }


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
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C701280.CreateSlaveOrder.Выставление заявки.Биржа не работает")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C701280() {
        //String SIEBEL_ID_SLAVE = "5-1YWVDYEZI";
        //contractIdSlave = "2047111824";
        //создаем мока для миддл
        mocksBasicSteps.TradingShedulesExchangeDefaultTime(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder,instrument.tickerAAPL, instrument.classCodeAAPL, "Sell","3", "3", "SPB_MORNING_WEEKEND");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,  "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);

/*        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerBANEP, instrument.tradingClearingAccountBANEP,
            "2", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "26551.10", masterPos);
        *///создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
/*        String baseMoneySl = "27000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerBANEP, instrument.tradingClearingAccountBANEP,
            "7", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);*/
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
//        Thread.sleep(5000);
        //смотрим, сообщение, которое поймали в топике kafka tracking.delay.command
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is("SYNCHRONIZE"));
    }


    @SneakyThrows
    @Test
    @AllureId("712128")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C712128.CreateSlaveOrder.Выставление заявки.ExecutionReportStatus ='Rejected'")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C712128() {
/*        String SIEBEL_ID_SLAVE = "4-LQB8FKN";
        contractIdSlave = "2054235441";*/
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrders(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, "Rejected", instrument.tickerAAPL, instrument.classCodeAAPL, "Buy","3", "3");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //Thread.sleep(5000);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        OffsetDateTime createdAt = slaveOrder2.getCreateAt().toInstant().atOffset(ZoneOffset.UTC);
        Instant createdAtSlaveOrder = createdAt.toInstant();
        //проверяем параметры SlaveOrder
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        //смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.SYNCHRONIZE));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("createAt в команды не равен", createdAtSlaveOrder.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
            is(createAt.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)));
}


    @SneakyThrows
    @Test
    @AllureId("849688")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C849688.CreateSlaveOrder.Выставление заявки.Отмена заявки и повторного выставления, executionReportStatus = 'Cancelled' И lotsExecuted = 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C849688() {
/*        contractIdSlave = "2092721501";
        String SIEBEL_ID_SLAVE = "5-2IMV74EF5";*/
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrders(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, "Cancelled", instrument.tickerAAPL, instrument.classCodeAAPL, "Buy","3", "0");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,
            "5", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
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
        assertThat("Операция не равна", commandKafka.getOperation().toString(), is("SYNCHRONIZE"));
    }



    @SneakyThrows
    @Test
    @AllureId("730132")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C730132.CreateSlaveOrder.Выставление заявки.Выставление заявки.Ошибка из списка настройки fatal-error-codes")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C730132() {
/*        String SIEBEL_ID_SLAVE = "1-3L0X4M1";
        contractIdSlave = "2065560563";*/
        mocksBasicSteps.createDataForMockCreateSlaveOrdersError(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, instrument.tickerAAPL, instrument.classCodeAAPL, "Buy", "Symbol not found for SecurityId(SPBXM,AAPL)", "SymbolNotFound");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
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
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);
    }


    @SneakyThrows
    @Test
    @AllureId("851304")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C851304.CreateSlaveOrder.Выставление заявки.Ошибка из списка настройки reject-error-codes")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C851304() {
/*        String SIEBEL_ID_SLAVE = "4-LQB8FKN";
        contractIdSlave = "2054235441";*/
        mocksBasicSteps.createDataForMockCreateSlaveOrdersError(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, instrument.tickerAAPL, instrument.classCodeAAPL, "Buy", "Ошибка тарифного модуля", "TariffModuleError");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);

//        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры SlaveOrder
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is("SYNCHRONIZE"));
    }


    // тест выполняется только с моком
    @SneakyThrows
    @Test
    @AllureId("1725860")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("1725860 Выставление заявки. Повторное выставление заявки, если в ответе New")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1725860() {
/*        String SIEBEL_ID_SLAVE = "4-LQB8FKN";
        contractIdSlave = "2054235441";*/
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrders(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, "New", instrument.tickerAAPL, instrument.classCodeAAPL, "Buy","3", "3");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        Thread.sleep(5000);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        OffsetDateTime createdAt = slaveOrder2.getCreateAt().toInstant().atOffset(ZoneOffset.UTC);
        Instant createdAtSlaveOrder = createdAt.toInstant();
        //проверяем параметры SlaveOrder
        Integer state = null;
        assertThat("State не равно", slaveOrder2.getState(), is(state));
        //смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.RETRY_SYNCHRONIZATION));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("createAt в команды не равен", createdAtSlaveOrder.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
            is(createAt.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("867332")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("867332 Выставление заявки. Вернулся успех, но executionReportStatus неизвестное значение")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C867332() {
/*        String SIEBEL_ID_SLAVE = "4-LQB8FKN";
        contractIdSlave = "2054235441";*/
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrders(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, "Welcome", instrument.tickerAAPL, instrument.classCodeAAPL, "Buy","3", "3");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        OffsetDateTime createdAt = slaveOrder2.getCreateAt().toInstant().atOffset(ZoneOffset.UTC);
        Instant createdAtSlaveOrder = createdAt.toInstant();
        //проверяем параметры SlaveOrder
        Integer state = null;
        assertThat("State не равно", slaveOrder2.getState(), is(state));
        //смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event commandKafka = Tracking.Event.parseFrom(message.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Action команды не равен", commandKafka.getAction().toString(), is("UPDATED"));
        assertThat("ContractId команды не равен", commandKafka.getContract().getId(), is(contractIdSlave));
        assertThat("Blocked команды не равен", commandKafka.getContract().getBlocked(), is(true));
        assertThat("createAt в команды не равен", createdAtSlaveOrder.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
            is(createAt.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)));
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(commandKafka);
        checkContractParam(contractIdSlave);
    }


    @SneakyThrows
    @Test
    @AllureId("867312")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("867312 Выставление заявки. Незнакомый payload.code")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C867312() {
/*        String SIEBEL_ID_SLAVE = "1-3L0X4M1";
        contractIdSlave = "2065560563";*/
        mocksBasicSteps.createDataForMockCreateSlaveOrdersError(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, instrument.tickerAAPL, instrument.classCodeAAPL, "Buy", "Trading don't work", "NotWorking");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.contract.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);
    }

    // тест выполняется только с моком
    @SneakyThrows
    @Test
    @AllureId("705844")
    @Tags({@Tag("qa2")})
    @DisplayName("705844 Выставление заявки. Ошибка из списка настройки retry-error-codes")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C705844() {
/*        String SIEBEL_ID_SLAVE = "1-3L0X4M1";
        contractIdSlave = "2065560563";*/
        mocksBasicSteps.createDataForMockCreateSlaveOrdersError(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, instrument.tickerAAPL, instrument.classCodeAAPL, "Buy", "Server Error", "INTERNAL_ERROR");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 4,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        Thread.sleep(5000);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        OffsetDateTime createdAt = slaveOrder2.getCreateAt().toInstant().atOffset(ZoneOffset.UTC);
        Instant createdAtSlaveOrder = createdAt.toInstant();
        //проверяем параметры SlaveOrder
        Integer state = null;
        assertThat("State не равно", slaveOrder2.getState(), is(state));
        //смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.RETRY_SYNCHRONIZATION));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("createAt в команды не равен", createdAtSlaveOrder.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
            is(createAt.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("867018")
    @Tags({@Tag("qa2")})
    @DisplayName("867018 Выставление заявки. Ошибка из списка настройки wait-error-codes")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C867018() {
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrdersError(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, instrument.tickerAAPL, instrument.classCodeAAPL, "Buy", "Wait Minute", "OrderDuplication");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,  "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(TEN_SECONDS).pollDelay(Duration.ofSeconds(3)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "0", instrument.classCodeAAPL,
            new BigDecimal(3), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("668243")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("668243 Не найдена exchange в кеш exchangeTradingScheduleCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C668243() {
        //String SIEBEL_ID_SLAVE = "5-1YWVDYEZI";
        //contractIdSlave = "2047111824";
        //создаем мока для миддл
        mocksBasicSteps.TradingShedulesExchangeDefaultTime(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder,instrument.tickerAAPL, instrument.classCodeAAPL, "Sell","3", "3", "SPB");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,  "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().pollDelay(Duration.ofNanos(500)).atMost(Duration.ofSeconds(2));
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(2)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);

    }

    // тест выполняется только с моком
    @SneakyThrows
    @Test
    @AllureId("867341")
    @Tags({@Tag("qa2")})
    @DisplayName("867341 Обработки частичного исполнения,executionReportStatus ='PartiallyFill' И lotsExecuted != 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C867341() {
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrders(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, "PartiallyFill", instrument.tickerAAPL, instrument.classCodeAAPL, "Buy","3", "2");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,  "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(TEN_SECONDS).pollDelay(Duration.ofSeconds(3)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры SlaveOrder
        BigDecimal lot = new BigDecimal(2);
        checkParamSlaveOrder(2, "1", "0", instrument.classCodeAAPL,
            lot, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("851506")
    @Tags({@Tag("qa2")})
    @DisplayName("851506 Обработки частичного исполнения,executionReportStatus ='Cancelled' И lotsExecuted != 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C851506() {
        //создаем мока для миддл
        mocksBasicSteps.createDataForMockCreateSlaveOrders(masterOrder, slaveOrder,
            mockSlaveDate.investIdMasterOrder, mockSlaveDate.investIdSlaveOrder, mockSlaveDate.contractIdMasterOrder, mockSlaveDate.contractIdSlaveOrder,
            mockSlaveDate.clientCodeSlaveOrder, "Cancelled", instrument.tickerAAPL, instrument.classCodeAAPL, "Buy","3", "2");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(masterOrder);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(slaveOrder);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,  "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(TEN_SECONDS).pollDelay(Duration.ofSeconds(3)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры SlaveOrder
        BigDecimal lot = new BigDecimal(2);
        checkParamSlaveOrder(2, "1", "0", instrument.classCodeAAPL,
            lot, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
    }



    /////////***методы для работы тестов**************************************************************************
    //проверяем, парамерты message события в топике tracking.event
    @Step("Проверяем параметры message события в топике tracking.event")
    void checkEventParam(Tracking.Event event) {
        assertThat("ID события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID договора не равен", event.getContract().getState().toString(), is("TRACKED"));
        assertThat("ID стратегии не равен", (event.getContract().getBlocked()), is(true));
    }

    //проверяем запись по контракту в табл. contract
    @Step("Проверяем параметры по контракту в табл. contract")
    void checkContractParam(String contractIdSlave) {
        contract = contractService.getContract(contractIdSlave);
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("tracked"));
        assertThat("статус клиента не равно", (contract.getBlocked()), is(true));
    }

    @Step("Проверяем параметры заявки в slave_order_2")
    void checkParamSlaveOrder(int version, String attemptsCount, String action, String classCode,
                              BigDecimal lots, String ticker, String tradingClearingAccount) {
        assertThat("Version портфеля slave не равно", slaveOrder2.getVersion(), is(version));
        assertThat("AttemptsCount не равно", slaveOrder2.getAttemptsCount().toString(), is(attemptsCount));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is(action));
        assertThat("ClassCode не равно", slaveOrder2.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder2.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(lots));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(ticker));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder2.getCreateAt(), is(notNullValue()));
    }


}
