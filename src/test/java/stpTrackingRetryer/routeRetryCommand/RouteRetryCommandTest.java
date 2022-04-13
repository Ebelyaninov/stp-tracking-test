package stpTrackingRetryer.routeRetryCommand;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
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
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("RouteRetryCommand - Маршрутизация отложенных команд для повторной отправки")
@Feature("TAP-8925")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-retryer")
@Tags({@Tag("stp-tracking-retryer"), @Tag("RouteRetryCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    KafkaAutoConfiguration.class

})
public class RouteRetryCommandTest {
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ProfileService profileService;
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
    StpSiebel siebel;

    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave = "2013919085";
    UUID strategyId;

    String ticker = "ABBV";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
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
//            try {
//                createEventInTrackingEvent(contractIdSlave);
//            } catch (Exception e) {
//            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("782994")
    @DisplayName("C782994.RouteRetryCommand.Повторная отправка по истечении времени")
    @Subfeature("Успешные сценарии")
    @Description("Операция для отправки отложенной команды в топик назначения по истечении временной задержки, равной 30 секундам.")
    void C782994() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelMasterRetryer)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelSlaveRetryer)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "6259.17", positionListMaster);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),null);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("3"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "7259.17";
        createSlavePortfolioWithOutPosition(2, 2, baseMoneySlave, positionListSl);
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1, 0,
            classCode, java.util.UUID.randomUUID(), new BigDecimal("90.18"), new BigDecimal("3"),
            (byte) 0, ticker, tradingClearingAccount, null);
        //вычитываем из топика кафка tracking.30.delay.retryer.command все offset
        resetOffsetToLate(TRACKING_30_DELAY_RETRYER_COMMAND);
        //отправляем команду tracking.delay.command:
        OffsetDateTime time = OffsetDateTime.now();
        createCommandTrackingDelayCommand(contractIdSlave, time);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_30_DELAY_RETRYER_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.SYNCHRONIZE));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdSlave));
        assertThat("createAt не равен", time.toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(createAt.truncatedTo(ChronoUnit.SECONDS)));
    }


    private static Stream<Arguments> provideStringsForAllExchange() {
        return Stream.of(
            Arguments.of("SPB", "ABBV", "TKCBM_TCAB", TRACKING_SPB_RETRYER_COMMAND, "90.18"),
            Arguments.of("MOEX_PLUS", "FXRB", "L01+00002F00", TRACKING_MOEXPLUS_RETRYER_COMMAND, "1826"),
            Arguments.of("MOEX", "MSTT", "L01+00000F00", TRACKING_MOEX_RETRYER_COMMAND, "84.4"),
            Arguments.of("SPB_MORNING", "INTC", "TKCBM_TCAB", TRACKING_SPB_MORNING_RETRYER_COMMAND, "142.55"),
            Arguments.of("FX", "USD000UTSTOM", "MB9885503216", TRACKING_FX_RETRYER_COMMAND, "74.2575"),
            Arguments.of("SPB_MORNING_WEEKEND", "FB", "TKCBM_TCAB", TRACKING_SPB_MORNING_WEEKEND_RETRYER_COMMAND, "500"),
            Arguments.of("SPB_WEEKEND", "SNAP", "L01+00000SPB", TRACKING_SPB_WEEKEND_RETRYER_COMMAND, "40"),
            Arguments.of("SPB_RU_MORNING", "FGEN", "TKCBM_TCAB", TRACKING_SPB_RU_MORNING_RETRYER_COMMAND, "63.2"),
            Arguments.of("MOEX_MORNING", "YNDX", "Y02+00001F00", TRACKING_MOEX_MORNING_RETRYER_COMMAND, "3517.6"),
            Arguments.of("FX_WEEKEND", "USDRUB", "MB9885503216", TRACKING_FX_WEEKEND_RETRYER_COMMAND, "120.38"),
            Arguments.of("FX_MTL", "USDRUB", "MB9885503216", TRACKING_FX_MTL_RETRYER_COMMAND, "127.38")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForAllExchange")
    @AllureId("783279")
    @DisplayName("C783279.RouteRetryCommand.Повторная отправка после начала торгов")
    @Subfeature("Успешные сценарии")
    @Description("Операция для отправки отложенной команды в топик назначения по истечении временной задержки, равной 30 секундам.")
    void C783279(String exchange, String ticker, String tradingClearingAccount, Topics topic, String price) {
        //вычитываем из топика кафка все offset
        resetOffsetToLate(topic);
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelMasterRetryer)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelSlaveRetryer)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "16259.17", positionListMaster);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),null);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("3"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "17259.17";
        createSlavePortfolioWithOutPosition(2, 2, baseMoneySlave, positionListSl);
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1, 0,
            classCode, java.util.UUID.randomUUID(), new BigDecimal(price), new BigDecimal("3"),
            (byte) 0, ticker, tradingClearingAccount, null);
        //отправляем команду tracking.delay.command:
        OffsetDateTime time = OffsetDateTime.now();
        createCommandTrackingDelayExCommand(contractIdSlave, exchange, time);
        //Смотрим, сообщение, которое поймали в топике kafka
        await().pollDelay(Duration.ofNanos(500));
        List<Pair<String, byte[]>> messagesFromRouteTopic = kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(5));
        List<Pair<String, byte[]>> messagesFromSlaveCommand = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(5));
        //Получаем событие из routeTopic
        Pair<String, byte[]> messageFromRouteTopic = messagesFromRouteTopic.stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено из топика " + topic));
        Tracking.PortfolioCommand commandKafkaFromRouteTopic = Tracking.PortfolioCommand.parseFrom(messageFromRouteTopic.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafkaFromRouteTopic.getCreatedAt().getSeconds(), commandKafkaFromRouteTopic.getCreatedAt().getNanos());
        //проверяем параметры команды из routeTopic (routeRetryCommand)
        assertThat("Operation команды не равен", commandKafkaFromRouteTopic.getOperation(), is(Tracking.PortfolioCommand.Operation.SYNCHRONIZE));
        assertThat("ContractId команды не равен", commandKafkaFromRouteTopic.getContractId(), is(contractIdSlave));
        //Получаем данные из slaveCommand (handle<Exchange>RetryCommand)
        Pair<String, byte[]> messageFromSlaveCommand = messagesFromSlaveCommand.stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено из топика slaveCommand"));
        Tracking.PortfolioCommand commandKafkaFromSlaveCommand = Tracking.PortfolioCommand.parseFrom(messageFromSlaveCommand.getValue());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafkaFromSlaveCommand.getOperation(), is(Tracking.PortfolioCommand.Operation.SYNCHRONIZE));
        assertThat("ContractId команды не равен", commandKafkaFromSlaveCommand.getContractId(), is(contractIdSlave));
    }


//// методы для работы тестов*************************************************************************

    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }


    // создаем команду в топик кафка tracking.master.command
    Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId) {
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.Event event = Tracking.Event.newBuilder()
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setContract(Tracking.Contract.newBuilder()
                .setId(contractId)
                .setState(Tracking.Contract.State.TRACKED)
                .setBlocked(false)
                .build())
            .build();
        return event;
    }

    Tracking.PortfolioCommand createCommandRetrySynchronize(String contractIdSlave, OffsetDateTime time) {
        //отправляем команду на синхронизацию
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.SYNCHRONIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .build();
        return command;
    }


    //метод отправляет команду с operation = 'RETRY_SYNCHRONIZATION'.
    void createCommandTrackingDelayCommand(String contractIdSlave, OffsetDateTime time) {
        //создаем команду
        Tracking.PortfolioCommand command = createCommandRetrySynchronize(contractIdSlave, time);
        log.info("Команда в tracking.delay.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
//        //создаем список заголовков
        Headers headers = new RecordHeaders();
        headers.add("destination.topic.name", "tracking.slave.command".getBytes());
        headers.add("delay.seconds", "30".getBytes());
        //отправляем команду в топик kafka tracking.delay.command
        kafkaSender.send(TRACKING_DELAY_COMMAND, contractIdSlave, eventBytes, headers);

    }


    //метод отправляет команду с operation = 'RETRY_SYNCHRONIZATION'.
    void createCommandTrackingDelayExCommand(String contractIdSlave, String exchange, OffsetDateTime time) throws InterruptedException {
        //создаем команду
        Tracking.PortfolioCommand command = createCommandRetrySynchronize(contractIdSlave, time);
        log.info("Команда в tracking.delay.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //создаем список заголовков
        Headers headers = new RecordHeaders();
        headers.add("destination.topic.name", "tracking.slave.command".getBytes());
        headers.add("exchange.name", exchange.getBytes());
        //отправляем команду в топик kafka tracking.delay.command
        kafkaSender.send(TRACKING_DELAY_COMMAND, contractIdSlave, eventBytes, headers);
    }


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.event
        kafkaSender.send(TRACKING_EVENT, contractIdSlave, eventBytes);
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null, null);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy());
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategy = trackingService.saveStrategy(strategy);
    }


    void createMasterPortfolio(int version, String money, List<MasterPortfolio.Position> positionList) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPosition, positionList);
    }

    //создаем портфель master в cassandra
    void createSlavePortfolioWithOutPosition(int version, int comparedToMasterVersion, String money,
                                             List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolio(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList);
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, null);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(false);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);

    }
}
