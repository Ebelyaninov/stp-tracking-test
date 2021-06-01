package stpTrackingApi.createStrategy;

import com.google.protobuf.BytesValue;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest;
import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufBytesReceiver;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufCustomReceiver;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
    KafkaProtobufFactoryAutoConfiguration.class
})

public class CreateStrategySuccessTest {

    @Resource(name = "customReceiverFactory")
    KafkaProtobufCustomReceiver<String, byte[]> kafkaReceiver;

    @Resource(name = "bytesReceiverFactory")
    KafkaProtobufBytesReceiver<String, BytesValue> receiverBytes;

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


    Client client;
    Contract contract;
    Strategy strategy;
    Profile profile;
    MasterPortfolio masterPortfolio;
    MasterPortfolioPositionRetention masterPortfolioPositionRetention;
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

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
        String title = "Тест стратегия Autotest 001 " + dateNow;
        String description = "New test стратегия Autotest 001 " + dateNow;
        String positionRetentionId = "days";

        //Находим клиента в social и берем данные по профайлу
        //В этом и последующих тестах временно отключаем логику обращение в сторонний сервис, т.к. это приводит к замедлению выолнения тестов
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
         */
        //Получаем данные по Master клиенту через API сервиса счетов
        GetBrokerAccountsResponse masterBrokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = masterBrokerAccount.getInvestId();
        contractId = masterBrokerAccount.getBrokerAccounts().get(0).getId();

        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);

        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(Currency.RUB);
        request.setDescription(description);
        request.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId(positionRetentionId);

        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());

        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");

        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);

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
        String title = "Тест стратегия Autotest 002 " + dateNow;
        String description = "New test стратегия Autotest 002 " + dateNow;
        String positionRetentionId = "days";

        //Находим клиента в social и берем данные по профайлу
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
         */

        //Получаем данные по Master клиенту через API сервиса счетов и Создаем запись o БД автоследование(db-tracking.trading.local) в табл. client
        GetBrokerAccountsResponse masterBrokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = masterBrokerAccount.getInvestId();
        contractId = masterBrokerAccount.getBrokerAccounts().get(0).getId();
        createClientWithContract(investId, ClientStatusType.registered, null, contractId, null, ContractState.untracked, null);

        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("10000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId(positionRetentionId);

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
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);

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
        String title = "Тест стратегия Autotest 003 " + dateNow;
        String description = "New test стратегия Autotest 003 " + dateNow;

        //Находим клиента в social и берем данные по профайлу
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
         */

        //Получаем данные по Master-клиенту через API сервиса счетов
        GetBrokerAccountsResponse masterBrokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = masterBrokerAccount.getInvestId();
        contractId = masterBrokerAccount.getBrokerAccounts().get(0).getId();

        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);

        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("5000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId("days");

        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        strategyId = expectedResponse.getStrategy().getId();

        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");

        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(expectedResponse.getStrategy().getId());
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);

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
        String title = "Тест стратегия Autotest 004 " + dateNow;
        String positionRetentionId = "days";

        //Находим клиента в social и берем данные по профайлу
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
         */

        //Получаем данные по Master-клиенту через API сервиса счетов
        GetBrokerAccountsResponse masterBrokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = masterBrokerAccount.getInvestId();
        contractId = masterBrokerAccount.getBrokerAccounts().get(0).getId();

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
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            //.execute(response -> response.as(CreateStrategyResponse.class));
            .execute(response -> response);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getBody().asString().substring(19, 55));

        //Проверяем мета-данные response, x-trace-id не пустое значение, x-server-time текущее время до минут
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //assertThat("x-server-time не равно", expectedResponse.getHeaders().getValue("x-server-time").substring(0, 16), is(dateNow));

        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");

        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(IsNull.nullValue()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
        assertThat("кол-во подписок на договоре не равно", strategy.getSlavesCount(), is(0));

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
    @AllureId("441790")
    @DisplayName("C441790.CreateStrategy. Параметры title и description с пробелами")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441790() {
        String title = "  Тест стратегия Autotest 005  ";
        String titleNew = "Тест стратегия Autotest 005";
        String description = "  New test стратегия Autotest 005  ";
        String positionRetentionId = "days";

        //Находим клиента в social и берем данные по профайлу
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
         */

        //Находим investId клиента через API сервиса счетов
        GetBrokerAccountsResponse masterBrokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = masterBrokerAccount.getInvestId();
        contractId = masterBrokerAccount.getBrokerAccounts().get(0).getId();

        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);

        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("7000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId(positionRetentionId);
        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        strategyId = expectedResponse.getStrategy().getId();

        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");

        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(expectedResponse.getStrategy().getId());
        checkParamStrategy(contractId, titleNew, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);

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
        String title = "Тест стратегия Autotest 006 " + dateNow;
        String description = "New test стратегия Autotest 006 " + dateNow;

        //Находим клиента в social и берем данные по профайлу
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
         */

        //Получаем данные по Master-клиенту через API сервиса счетов
        GetBrokerAccountsResponse masterBrokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = masterBrokerAccount.getInvestId();
        contractId = masterBrokerAccount.getBrokerAccounts().get(0).getId();

        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);

        //Формируем тело для запроса createStrategy
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setTitle(title);
        request.setDescription(description);
        request.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        request.setPositionRetentionId(retentionForStrategy);

        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());

        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");

        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);

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
        String title = "Тест стратегия Autotest 007 - INITIALIZE";
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

        //Находим клиента в social и берем данные по профайлу
        /*
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname());
            .setImage(profile.getImage().toString());
         */

        //Находим investId клиента через API сервиса счетов
        GetBrokerAccountsResponse brokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = brokerAccount.getInvestId();
        contractId = brokerAccount.getBrokerAccounts().get(0).getId();

        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        BigDecimal baseMoney = new BigDecimal("12000.0");

        //Вычитываем из топика kafka: tracking.master.command все offset
        resetOffsetToLate(Topics.TRACKING_MASTER_COMMAND);

        //Формируем тело запроса
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId(positionRetentionId);

        //Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = createStrategy(SIEBEL_ID, request);
        //Достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());

        //Смотрим, сообщение, пойманное в топике kafka
        Map<String, byte[]> message = await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(Topics.TRACKING_MASTER_COMMAND.getName()), is(not(empty()))
            )
            .stream().findFirst().orElseThrow(() -> new RuntimeException("Сообщений не получено"));

        portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.values().stream().findAny().get());
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
        checkParamStrategy(contractId, title, Currency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);
        //Находим запись о портфеле мастера в Cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }


    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile);
    }

    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client и tracking.contract
    void createClientWithContract(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile, String contractId,
                                  ContractRole contractRole, ContractState contractState, UUID strategyId) {
        client = clientService.createClient(investId, clientStatusType, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
    }

    //Метод для получения инфо о клиенте через API - сервиса счетов
    @Step("Получение инфо об аккаунте клиента через API сервиса счетов")
    GetBrokerAccountsResponse getBrokerAccountByAccountPublicApi(String siebelId) {
        GetBrokerAccountsResponse resBrokerAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelId)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery("false")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resBrokerAccount;
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
                            String status, StrategyRiskProfile riskProfile, int slaveCount) {
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
                    strategy.getSlavesCount(), is(0))
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
        log.info("Получен запрос на считывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> receiverBytes.receiveBatch(topic.getName(),
                Duration.ofSeconds(3), BytesValue.class), List::isEmpty);
        log.info("Все сообщения из {} топика прочитаны", topic.getName());
    }
}