package stpTrackingApi.createStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse;
import ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency;
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
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.Matchers.notNullValue;

@Epic("createStrategy - Создание стратегии")
@Feature("TAP-6805")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class})
public class CreateStrategySuccessTest {
    KafkaHelper kafkaHelper = new KafkaHelper();

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
    StrategyService strategyService;


    Client client;
    Contract contract;
    Strategy strategy;
    Profile profile;
    MasterPortfolio masterPortfolio;
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    @BeforeAll
    void conf() {
        strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {//
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
        });
    }

    String contractId;
    UUID strategyId;
    String SIEBEL_ID = "5-EVTLCGZ5";


    @Test
    @AllureId("263384")
    @DisplayName("C263384.CreateStrategy.Успешное создание стратегии, подтвержденный ведущий")
    @Subfeature("Успешные сценарии")
    @Description(" Метод создания стратегии на договоре ведущего")
    void C263384() {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "тест стратегия autotest " + dateNow;
        String description = "new test стратегия autotest " + dateNow;
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        //формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("15000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        // Вызываем метод CreateStrategy
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse.class));
        //достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, StrategyBaseCurrency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }


    @Test
    @AllureId("265061")
    @DisplayName("C265061.Успешное создание стратегии, на уже зарегистрированный договор")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C265061() {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "тест стратегия02 " + dateNow;
        String description = "new test стратегия02 " + dateNow;
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //находим клиента в сервисе счетов и Создаем запись o БД автоследование(db-tracking.trading.local) в табл. client
        createClientWithContract(investId, ClientStatusType.registered, socialProfile, contractId,
            null, ContractState.untracked, null);
        //формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("10000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        // Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getBody().asString().substring(19, 55));
        //проверяем мета-данные response, x-trace-id не пустое значение, x-server-time текущее время до минут
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, StrategyBaseCurrency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }


    @Test
    @AllureId("431480")
    @DisplayName("C431480.Повторный вызов метода, после успешно созданной стратегии")
    @Subfeature("Успешные сценарии")
    @Description(" Метод создания стратегии на договоре ведущего")
    void C431480() throws InterruptedException {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "тест стратегия03 " + dateNow;
        String description = "new test стратегия03 " + dateNow;
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        //формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("5000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        // Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(CreateStrategyResponse.class));
        strategyId = expectedResponse.getStrategy().getId();
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(expectedResponse.getStrategy().getId());
        checkParamStrategy(contractId, title, StrategyBaseCurrency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);
        //находим запись о портеле мастера в cassandra
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
    @DisplayName("C443462.Успешное создание стратегии без параметра description")
    @Subfeature("Успешные сценарии")
    @Description(" Метод создания стратегии на договоре ведущего")
    void C443462() {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "тест стратегия04 " + dateNow;
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        //формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("3000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        // Вызываем метод CreateStrategy
//        CreateStrategyResponse expectedResponse = strategyApi.createStrategy()
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(CreateStrategyResponse.class));
            .execute(response -> response);
        //достаем из response идентификатор стратегии
        strategyId = UUID.fromString(expectedResponse.getBody().asString().substring(19, 55));
        //проверяем мета-данные response, x-trace-id не пустое значение, x-server-time текущее время до минут
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
//        assertThat("x-server-time не равно", expectedResponse.getHeaders().getValue("x-server-time").substring(0, 16), is(dateNow));
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(IsNull.nullValue()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
        assertThat("кол-во подписок на договоре не равно", strategy.getSlavesCount(), is(0));
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }


    @Test
    @AllureId("441790")
    @DisplayName("C441790.CreateStrategy.Параметры title и description с пробелами")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441790() {
        String title = "  тест стратегия CreateStrategy12  ";
        String titleNew = "тест стратегия CreateStrategy12";
        String description = " new test стратегия autotest12  ";
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в API сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        //формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("7000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        // Вызываем метод CreateStrategy
        CreateStrategyResponse expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(CreateStrategyResponse.class));
        strategyId = expectedResponse.getStrategy().getId();
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = contractService.getContract(contractId);
        checkParamContract(contractId, investId, "untracked");
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(expectedResponse.getStrategy().getId());
        checkParamStrategy(contractId, titleNew, StrategyBaseCurrency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }


    @Test
    @AllureId("615712")
    @DisplayName("C615712.CreateStrategy.Инициализация портфеля master'а, после создания стратегии")
    @Subfeature("Успешные сценарии")
    @Description(" Метод создания стратегии на договоре ведущего")
    void C615712() throws Exception {
        String SIEBEL_ID = "5-UGUF8MA5";
        String title = "тест стратегия autotest INITIALIZE";
        String description = "new test стратегия autotest INITIALIZE";
        Tracking.PortfolioCommand portfolioCommand = null;
        LocalDateTime dateCreateTr = null;
        UUID strategyRest = null;
        String key = null;
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в API сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        BigDecimal baseMoney = new BigDecimal("12000.0");
        //включаем kafkaConsumer и слушаем топик tracking.master.command
        List<KafkaMessageConsumer.Record<String, byte[]>> records = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);
            messageConsumer.startUp();
            //формируем тело запроса
            ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
            request.setContractId(contractId);
            request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
            request.setDescription(description);
            request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
            request.setTitle(title);
            request.setBaseMoneyPositionQuantity(baseMoney);
            //Вызываем метод CreateStrategy
            ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse expectedResponse = strategyApi.createStrategy()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .xDeviceIdHeader("new")
                .xTcsSiebelIdHeader(SIEBEL_ID)
                .body(request)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse.class));
            //достаем из response идентификатор стратегии
            strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
            //ловим команду, в топике kafka tracking.master.command
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractId.equals(portfolioCommandBefore.getContractId())
                & ("INITIALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
//      проверяем команду н первичную инициализацию портфеля мастера
        long unscaled = 120000;
        assertThat("ID договора мастера не равен", portfolioCommand.getContractId(), is(contractId));
        assertThat("operation команды по инициализации мастера не равен", portfolioCommand.getOperation().toString(), is("INITIALIZE"));
        assertThat("ключ команды по инициализации мастера  не равен", key, is(contractId));
        assertThat("номер версии по инициализации мастера  не равен", portfolioCommand.getPortfolio().getVersion(), is(1));
        assertThat("Объем позиции в базовой валюте мастера: unscaled  не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition()
            .getQuantity().getUnscaled(), is(unscaled));
        assertThat("Объем позиции в базовой валюте мастера: scaled  не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition()
            .getQuantity().getScale(), is(1));
        contract = contractService.getContract(contractId);
        assertThat("роль клиента не равно null", (contract.getRole()), is(nullValue()));
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("untracked"));
        strategy = strategyService.getStrategy(strategyId);
        checkParamStrategy(contractId, title, StrategyBaseCurrency.RUB, description, "draft", StrategyRiskProfile.CONSERVATIVE, 0);
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        checkParamMasterPortfolio(1, baseMoney);
    }


    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile);
    }


    //Метод находит подходящий siebelId в сервисе счетов и создаем запись по нему в табл. tracking.client и tracking.contract
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

    void checkParamContract(String contractId, UUID investId, String state) {
        assertThat("номера договоров не равно", contract.getId(), is(contractId));
        assertThat("номера клиента не равно", contract.getClientId(), is(investId));
        assertThat("роль клиента не равно null", (contract.getRole()), is(nullValue()));
        assertThat("статус клиента не равно", (contract.getState()).toString(), is(state));
        assertThat("номер стратегии клиента не равно", contract.getStrategyId(), is(nullValue()));
    }

    void checkParamStrategy(String contractId, String title, StrategyBaseCurrency currency, String description,
                            String status, StrategyRiskProfile riskProfile, int slaveCount) {
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(currency.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
        assertThat("кол-во подписок на договоре не равно", strategy.getSlavesCount(), is(0));
    }

    void checkParamMasterPortfolio(int version, BigDecimal baseMoney) {
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(version));
        assertThat("позиция по базовой валюте портеля мастера не равно", (masterPortfolio.getBaseMoneyPosition().getQuantity()), is(baseMoney));
    }
}
