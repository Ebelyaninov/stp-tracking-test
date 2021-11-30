package stpTrackingApi.createStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.core.IsNull;
import org.json.JSONObject;
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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioPositionRetention;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioPositionRetentionDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.*;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.parameter;
import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("createStrategy - Создание стратегии")
@Feature("TAP-6805")
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class

})

public class CreateStrategySuccessTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    StrategyApi strategyApi;
    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioPositionRetentionDao masterPortfolioPositionRetentionDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    StpTrackingApiSteps steps;

    Client client;
    Contract contract;
    Strategy strategy;
    MasterPortfolio masterPortfolio;
    MasterPortfolioPositionRetention masterPortfolioPositionRetention;


    @BeforeAll
    void conf() {
        strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractId, strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioPositionRetentionDao.deleteMasterPortfolioPositionRetention(strategyId);
            } catch (Exception e) {
            }
        });
    }

    String contractId;
    UUID strategyId;
    String SIEBEL_ID = "5-EVTLCGZ5";

    @Test
    @AllureId("263384")
    @DisplayName("C263384.CreateStrategy. Успешное создание стратегии, подтвержденный ведущий")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C263384() {
        //Получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "Autotest 001" + dateNow;
        String description = "New test стратегия Autotest 001 " + dateNow;
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = createStrategyRequest(Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE,
            0, "0", "0");
        //Находим запись о портфеле мастера в Cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
        //Находим значение времени удержания позиции в Cassandra invest_tracking.master_portfolio_position_retention
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioPositionRetention =
                masterPortfolioPositionRetentionDao.getMasterPortfolioPositionRetention(strategyId), notNullValue());
        checkParamMasterPortfolioPositionRetention(positionRetentionId);
    }


    @Test
    @AllureId("265061")
    @DisplayName("C265061.CreateStrategy. Успешное создание стратегии, на уже зарегистрированный договор")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C265061() {
        //Получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "Autotest 002" + dateNow;
        String description = "New test стратегия Autotest 002 " + dateNow;
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.03);
//        feeRate.setResult(0.1);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        createClientWithContract(investId, ClientStatusType.registered, null, contractId, null, ContractState.untracked, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("10000.0");
        CreateStrategyRequest request = createStrategyRequest(Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getBody().asString().substring(19, 55));
        //Проверяем мета-данные response, x-trace-id не пустое значение, x-server-time текущее время до минут
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE,
            0, "0", "0");
        //Находим запись о портфеле мастера в Cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
        //Находим значение времени удержания позиции в Cassandra invest_tracking.master_portfolio_position_retention
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioPositionRetention =
                masterPortfolioPositionRetentionDao.getMasterPortfolioPositionRetention(strategyId), notNullValue());
        checkParamMasterPortfolioPositionRetention(positionRetentionId);
    }


    @Test
    @AllureId("431480")
    @DisplayName("C431480.CreateStrategy. Повторный вызов метода, после успешно созданной стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C431480() throws InterruptedException {
        //Получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "Autotest 003" + dateNow;
        String description = "New test стратегия Autotest 003 " + dateNow;
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.05);
//        feeRate.setResult(0.5);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("5000.0");
        CreateStrategyRequest request = createStrategyRequest(Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        strategyId = expectedResponse.getStrategy().getId();
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(expectedResponse.getStrategy().getId());
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE,
            0, "0", "0");
        //Находим запись о портфеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
        //Вызываем метод второй раз
        strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response.as(CreateStrategyResponse.class));
        List<Strategy> strategyOpt = strategyService.findListStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.size(), is(1));
    }


    @Test
    @AllureId("443462")
    @DisplayName("C443462.CreateStrategy. Успешное создание стратегии без параметра description")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C443462() {
        //Получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "Autotest 004" + dateNow;
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Получаем данные по Master-клиенту через API сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("3000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId(positionRetentionId);
//        request.setFeeRate(feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            //.execute(response -> response.as(CreateStrategyResponse.class));
            .execute(response -> response);

        assertThat("номера стратегии не равно", expectedResponse.getBody().jsonPath().get("errorCode"), is("Error"));
        assertThat("номера стратегии не равно", expectedResponse.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("441790")
    @DisplayName("C441790.CreateStrategy. Параметры title и description с пробелами")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441790() {
        String title = "  Autotest 005  ";
        String titleNew = "Autotest 005";
        String description = "  New test стратегия Autotest 005  ";
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("7000.0");
        CreateStrategyRequest request = createStrategyRequest(Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        strategyId = expectedResponse.getStrategy().getId();
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(expectedResponse.getStrategy().getId());
        checkParamStrategy(contractId, titleNew, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE,
            0, "0", "0");
        //Находим запись о портфеле мастера в Cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
        //Находим значение времени удержания позиции в Cassandra invest_tracking.master_portfolio_position_retention
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioPositionRetention =
                masterPortfolioPositionRetentionDao.getMasterPortfolioPositionRetention(strategyId), notNullValue());
        checkParamMasterPortfolioPositionRetention(positionRetentionId);
    }

    //Стрим допустимых значений - параметра positionRetentionId
    private static Stream<String> retentionForStrategy() {
        return Stream.of("days", "weeks", "months", "forever");
    }

    @ParameterizedTest
    @MethodSource("retentionForStrategy")
    @AllureId("920608")
    @DisplayName("C920608.CreateStrategy. Успешное создание стратегии, со всеми возможными значениями positionRetentionId")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C920608(String retentionForStrategy) throws ParseException {
        parameter("retentionPeriod", retentionForStrategy);
        //Получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "Autotest 006" + dateNow;
        String description = "New test стратегия Autotest 006 " + dateNow;
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело для запроса createStrategy
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = createStrategyRequest(Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, retentionForStrategy, feeRate);
        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE,
            0, "0", "0");
        //Находим запись о портфеле мастера в Cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
        //Находим значение времени удержания позиции в Cassandra invest_tracking.master_portfolio_position_retention
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioPositionRetention =
                masterPortfolioPositionRetentionDao.getMasterPortfolioPositionRetention(strategyId), notNullValue());
        checkParamMasterPortfolioPositionRetention(retentionForStrategy);
    }


    @Test
    @AllureId("615712")
    @DisplayName("C615712.CreateStrategy. Инициализация портфеля master'а, после создания стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C615712() throws Exception {
        String SIEBEL_ID = "5-20IBIUPTE";
        String title = "Тест стратегия 007";
        String description = "Тew test стратегия Autotest 007 - INITIALIZE";
        String positionRetentionId = "days";
        Tracking.PortfolioCommand portfolioCommand = null;
        LocalDateTime dateCreateTr = null;
        UUID strategyRest = null;
        String key = null;
        //Получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим investId клиента через API сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        BigDecimal baseMoney = new BigDecimal("12000.0");
        //Вычитываем из топика kafka: tracking.master.command все offset
        resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //Формируем тело запроса
        CreateStrategyRequest request = createStrategyRequest(Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        key = portfolioCommand.getContractId();
        log.info("Команда в tracking.master.command: {}", portfolioCommand);
        //Проверяем команду на первичную инициализацию портфеля мастера
        long unscaled = 120000;
        assertThat("ID договора мастера не равен", portfolioCommand.getContractId(), is(contractId));
        assertThat("operation команды по инициализации мастера не равен", portfolioCommand.getOperation().toString(), is("INITIALIZE"));
        assertThat("ключ команды по инициализации мастера не равен", key, is(contractId));
        assertThat("номер версии по инициализации мастера не равен", portfolioCommand.getPortfolio().getVersion(), is(1));
        assertThat("Объем позиции в базовой валюте мастера: unscaled не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition()
            .getQuantity().getUnscaled(), is(unscaled));
        assertThat("Объем позиции в базовой валюте мастера: scaled не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition()
            .getQuantity().getScale(), is(1));
        contract = contractService.getContract(contractId);
        assertThat("роль клиента не равно null", (contract.getRole()), is(nullValue()));
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("untracked"));
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE,
            0, "0", "0");
        //Находим запись о портфеле мастера в Cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }



    private static Stream<Arguments> strategy() {
        return Stream.of(
            Arguments.of(ru.qa.tinkoff.tracking.entities.enums.StrategyStatus.draft, null),
            Arguments.of(ru.qa.tinkoff.tracking.entities.enums.StrategyStatus.active, LocalDateTime.now())
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("strategy")
    @AllureId("1515398")
    @DisplayName("C1515398.CreateStrategy.На договор уже создана др. стратегия")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C1515398(ru.qa.tinkoff.tracking.entities.enums.StrategyStatus strategyStatus, LocalDateTime date) {
        String title = steps.getTitleStrategy();
        String description = "Тew test стратегия Autotest 007 - INITIALIZE";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractId, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            strategyStatus, 0, date, false);
        strategy = strategyService.getStrategy(strategyId);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createSignal = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("К договору уже привязана другая торговая стратегия"));
    }



    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
    }

    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client и tracking.contract
    void createClientWithContract(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile, String contractId,
                                  ContractRole contractRole, ContractState contractState, UUID strategyId) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
    }


    @Step("Создание стратегии, вызов метода createStrategy")
    CreateStrategyResponse createStrategy(String siebelId, CreateStrategyRequest request) {
        CreateStrategyResponse createdStrategy = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(siebelId)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(CreateStrategyResponse.class));
        return createdStrategy;
    }

    void checkParamContract(String contractId, UUID investId, String state) {
        step("Проверка полей записи о контракте клиента - PostgreSQL: tracking.contract", () -> {
            assertAll(
                () -> assertThat("Номер договора не совпадает с ожидаемым номером",
                    contract.getId(), is(equalTo(contractId))),
                () -> assertThat("Номер клиента не совпадает с ожидаемым номером",
                    contract.getClientId(), is(equalTo(investId))),
                () -> assertThat("Роль клиента отлична от null",
                    contract.getRole(), is(nullValue())),
                () -> assertThat("Статус клиента не совпадает с ожидаемым статусом",
                    contract.getState().toString(), is(equalTo(state))),
                () -> assertThat("Номер стратегии клиента отличен от null",
                    contract.getStrategyId(), is(nullValue())));
        });
    }

    void checkParamStrategy(String contractId, String title, Currency currency, String description,
                            String status, StrategyRiskProfile riskProfile, int slaveCount,
                            String result, String management) {
        step("Проверка полей записи о стратегии - PostgreSQL: tracking.strategy", () -> {
            assertAll(
                () -> assertThat("номер договора клиента не совпадает с ожидаемым номером",
                    strategy.getContract().getId(), is(equalTo(contractId))),
                () -> assertThat("название стратегии не равно ожидаемому названию",
                    strategy.getTitle(), is(title)),
                () -> assertThat("валюта стратегии не соварадает с ожидаемой валютой",
                    strategy.getBaseCurrency().toString(), is(currency.toString())),
                () -> assertThat("описание стратегии не совпадает с ожидаемым описанием",
                    strategy.getDescription(), is(description)),
                () -> assertThat("статус стратегии не равен ожидаемому статусу",
                    strategy.getStatus().toString(), is(status)),
                () -> assertThat("риск-профиль стратегии не совпадает с ожидаемым риск-профилем",
                    strategy.getRiskProfile().toString(), is(StrategyRiskProfile.CONSERVATIVE.toString())),
                () -> assertThat("кол-во подписок на договоре отлично от нуля",
                    strategy.getSlavesCount(), is(0)),
                () -> assertThat("кол-во подписок на договоре отлично от нуля",
                    strategy.getSlavesCount(), is(0)),
                () -> assertThat("ставка комиссии за результат",
                    strategy.getFeeRate().get("result").toString(), is(result)),
                () -> assertThat("ставка комиссии за управление",
                    strategy.getFeeRate().get("management").toString(), is(management))
            );
        });
    }

    void checkParamMasterPortfolio(int version, BigDecimal baseMoney) {
        step("Проверка полей портфеля мастера - Cassandra: invest_tracking.master_portfolio", () -> {
            assertAll(
                () -> assertThat("Значение версии портфеля мастера совпадает с ожидаемым",
                    masterPortfolio.getVersion(), is(equalTo(version))),
                () -> assertThat("Значение позиции по базовой валюте портфеля мастера совпадает с ожидаемым",
                    masterPortfolio.getBaseMoneyPosition().getQuantity(), is(equalTo(baseMoney))));
        });
    }

    void checkParamMasterPortfolioPositionRetention(String actualPositionRetention) {
        step("Проверка поля value таблицы - Cassandra: invest_tracking.master_portfolio_position_retention", () -> {
            assertThat("Значение поля совпадает с ожидаемым",
                masterPortfolioPositionRetention.getValue(), is(equalTo(actualPositionRetention)));
        });
    }

    @Step("Перемещаем offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }


    //ToDo FeeRate was disabled, StrategyFeeRate feeRate
    CreateStrategyRequest createStrategyRequest(Currency currency, String contractId, String description,
                                                StrategyRiskProfile strategyRiskProfile, String title,
                                                BigDecimal basemoney, String positionRetentionId,
                                                String feeRate) {
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(currency);
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(strategyRiskProfile);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        createStrategyRequest.setPositionRetentionId(positionRetentionId);
//        createStrategyRequest.setFeeRate(feeRate);
        return createStrategyRequest;
    }
}