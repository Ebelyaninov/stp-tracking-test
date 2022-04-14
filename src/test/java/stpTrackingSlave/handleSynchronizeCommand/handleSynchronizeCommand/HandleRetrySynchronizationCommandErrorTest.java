package stpTrackingSlave.handleSynchronizeCommand.handleSynchronizeCommand;

import extenstions.RestAssuredExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("handleSynchronizeCommand - Обработка команд на повторную синхронизацию")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleSynchronizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class HandleRetrySynchronizationCommandErrorTest {
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService kafkaStringSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ByteToByteSenderService kafkaByteSender;
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

    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Client clientSlave;
    Contract contract;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave = "2039667312";
    UUID strategyId;
    long subscriptionId;

    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    String SIEBEL_ID_SLAVE = "4-19BN28QJ";

    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";
    final String tickerApple = "AAPL";
    final String tradingClearingAccountApple = "TKCBM_TCAB";

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

    //ToDO Операцию отключили
//    @SneakyThrows
//    @Test
//    @AllureId("739012")
//    @DisplayName("C739012.HandleRetrySynchronizationCommand.Ошибка на анализе портфеля slave'а относительно портфеля master'а")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
//    void C739012() {
//        String ticker = "TECH";
//        String tradingClearingAccount = "L01+00002F00";
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
////       создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
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
//        //создаем портфель для slave
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
//            new BigDecimal("-0.0211"), new BigDecimal("-2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //вычитываем все события из tracking.event
//        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        //смотрим, что заявка не выставлялась
//        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
//        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
//        // ловим событие о блокировке slave в топике tracking.event
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
//        Pair<String, byte[]> message = messages.stream()
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
//        log.info("Событие  в tracking.contract.event:  {}", event);
//        //проверяем, данные в сообщении
//        checkParamEvent(event, "UPDATED", "TRACKED", true);
//        //находим в БД автоследования contract
//        contract = contractService.getContract(contractIdSlave);
//        checkParamContract("tracked", true);
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("739015")
//    @DisplayName("C739015.HandleRetrySynchronizationCommand.Ошибка при выставлении заявки")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
//    void C739015() {
//        String ticker = "WLH";
//        String tradingClearingAccount = "TKCBM_TCAB";
//        String classCode = "SPBXM";
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
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
//        //создаем портфель для slave
//        String baseMoneySl = "7000.0";
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
//            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
//            new BigDecimal("-0.0211"), new BigDecimal("-2"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        //вычитываем все события из tracking.event
//        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        //смотрим заявку
//        await().atMost(FIVE_SECONDS).until(() ->
//            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
//        // ловим событие о блокировке slave в топике tracking.event
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
//        Pair<String, byte[]> message = messages.stream()
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
//        log.info("Событие  в tracking.contract.event:  {}", event);
//        //проверяем, данные в сообщении
//        checkParamEvent(event, "UPDATED", "TRACKED", true);
//        //находим в БД автоследования contract
//        contract = contractService.getContract(contractIdSlave);
//        checkParamContract("tracked", true);
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("739006")
//    @DisplayName("C739006.HandleRetrySynchronizationCommand.У contractId blocked = true")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
//    void C739006() {
//        steps.createDataToMarketData(ticker, classCode, "108.09", "107.79", "107.72");
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
////       создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
//        strategyId = UUID.randomUUID();
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "7", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
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
//            new BigDecimal("0.593"), new BigDecimal("4"));
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
//            baseMoneySl, date, createListSlaveOnePos);
//        steps.createEventInTrackingEventWithBlock(contractIdSlave, true);
//        contract = contractService.updateBlockedContract(contractIdSlave, true);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        //смотрим, что заявка не выставлялась
//        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
//        assertThat("запись по заявке не равно", order.isPresent(), is(false));
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("739008")
//    @DisplayName("C739008.HandleRetrySynchronizationCommand.Не найден портфель slave'a в бд Cassandra в таблице slave_portfolio")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
//    void C739008() {
//        steps.createDataToMarketData(ticker, classCode, "108.09", "107.79", "107.72");
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
//        strategyId = UUID.randomUUID();
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
//            "7", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
//        //создаем подписку slave
//        //создаем подписку для  slave
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, false);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //вычитываем все события из tracking.event
//        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        //смотрим, что заявка не выставлялась
//        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
//        assertThat("запись по заявке не равно", order.isPresent(), is(false));
//        // ловим событие о блокировке slave в топике tracking.event
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
//        Pair<String, byte[]> message = messages.stream()
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
//        log.info("Событие  в tracking.contract.event" +
//            ":  {}", event);
//        //проверяем, данные в сообщении
//        checkParamEvent(event, "UPDATED", "TRACKED", true);
//        //находим в БД автоследования contract
//        contract = contractService.getContract(contractIdSlave);
//        checkParamContract("tracked", true);
//    }
//
//    @SneakyThrows
//    @Test
//    @AllureId("1249459")
//    @DisplayName("C1249459.Проверяем показатели подписки - у подписки blocked = true")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
//    void C1249459() {
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
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
//        steps.createMasterPortfolio(contractIdMaster, strategyId,1, "7000",  positionListMaster);
//        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
//            "5", date,        1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
//        //создаем подписку для slave c заблокированной подпиской
//        OffsetDateTime startSubTime = OffsetDateTime.now();
//        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
//            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
//            null, true);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
//        subscriptionId = subscription.getId();
//        //создаем портфель для ведомого
//        String baseMoneySlave = "6576.23";
//        List<SlavePortfolio.Position> positionList = new ArrayList<>();
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
//            baseMoneySlave, date, positionList);
//        //отправляем команду на синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        //получаем портфель slave
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
//        checkSlavePortfolioParameters(1,1,"6576.23");
//    }
//
//    @SneakyThrows
//    @Test
//    @AllureId("1510159")
//    @DisplayName("C1510159.Портфель синхронизируется - выставляем ту же заявку. Не нашли данные в кэше exchangePositionCache")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C1510159 () {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        BigDecimal lot = new BigDecimal("1");
//        BigDecimal orderQty = new BigDecimal("3");
//        BigDecimal priceOrder = new BigDecimal("11.11");
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
//            0, classCode, UUID.randomUUID(), priceOrder, orderQty,
//            null, "AAPL2", tradingClearingAccount, new BigDecimal("0"));
//        //вычитываем все события из tracking.event
//        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Contract getContract = contractService.getContract(contractIdSlave);
//
//        if (getContract.getBlocked() == false){
//            Thread.sleep(10000);
//        }
//
//        // ловим событие о блокировке slave в топике tracking.event
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(30));
//        Pair<String, byte[]> message = messages.stream()
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
//        log.info("Событие  в tracking.contract.event:  {}", event);
//        //проверяем, данные в сообщении
//        checkParamEvent(event, "UPDATED", "TRACKED", true);
//        //находим в БД автоследования contract
//        contract = contractService.getContract(contractIdSlave);
//        checkParamContract("tracked", true);
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("1510349")
//    @DisplayName("C1510349.Портфель синхронизируется - выставляем ту же заявку. Не нашли данные в кэше exchangePositionCache")
//    @Subfeature("Успешные сценарии")
//    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
//    void C1510349 () {
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        BigDecimal lot = new BigDecimal("1");
//        BigDecimal orderQty = new BigDecimal("3");
//        BigDecimal priceOrder = new BigDecimal("11.11");
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
//        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 126,
//            0, classCode, UUID.randomUUID(), priceOrder, orderQty,
//            null, ticker, tradingClearingAccount, new BigDecimal("0"));
//        //вычитываем все события из tracking.event
//        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
//        //отправляем команду на  повторную синхронизацию
//        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
//        Contract getContract = contractService.getContract(contractIdSlave);
//
//        if (getContract.getBlocked() == false){
//            Thread.sleep(90000);
//        }
//
//        // ловим событие о блокировке slave в топике tracking.event
//        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(60));
//        Pair<String, byte[]> message = messages.stream()
//            .filter(ms ->  ms.getKey().equals(contractIdSlave))
//            .findFirst()
//            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
//        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
//        log.info("Событие  в tracking.contract.event:  {}", event);
//        //проверяем, данные в сообщении
//        checkParamEvent(event, "UPDATED", "TRACKED", true);
//        //находим в БД автоследования contract
//        contract = contractService.getContract(contractIdSlave);
//        checkParamContract("tracked", true);
//    }





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


    void checkParamEvent(Tracking.Event event, String action, String state, boolean blocked) {
        assertThat("Action события не равен", event.getAction().toString(), is(action));
        assertThat("State договора не равен", event.getContract().getState().toString(), is(state));
        assertThat("Blocked договора не равен", (event.getContract().getBlocked()), is(blocked));
    }

    void checkParamContract(String state, boolean blocked) {
        assertThat("статус клиента не равно", (contract.getState()).toString(), is(state));
        assertThat("Blocked клиента не равно", (contract.getBlocked()), is(blocked));
    }


}
