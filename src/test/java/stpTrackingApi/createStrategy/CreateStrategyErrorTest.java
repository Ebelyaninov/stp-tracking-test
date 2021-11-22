package stpTrackingApi.createStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
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
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("createStrategy - Создание стратегии")
@Feature("TAP-6805")
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    KafkaAutoConfiguration.class
})

public class CreateStrategyErrorTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ProfileService profileService;
    @Autowired
    StpTrackingApiSteps steps;

    Client client;
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    String SIEBEL_ID = "1-5RLRHAS";
    String siebelIdNotOpen = "1-1PFLDYR";
    String siebelIdNotBroker = "5-M5JWUQE8";

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            clientService.deleteClient(client);
        });
    }

    private static Stream<Arguments> provideStringsForHeadersCreateStrategy() {
        return Stream.of(
            Arguments.of(null, "android", "4.5.6"),
            Arguments.of("trading-invest", null, "I.3.7"),
            Arguments.of("trading", "ios 8.1", null)
        );
    }
    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCreateStrategy")
    @AllureId("442935")
    @DisplayName("C442935.CreateStrategy. Валидация запроса: обязательные входные параметры в headers: X-APP-NAME, X-APP-VERSION, X-APP-PLATFORM")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C442935(String name, String version, String platform) {
        String title = "Тест стратегия CreateStrategy Autotest 001";
        String description = "New test стратегия Autotest 001";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title,basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            createStrategy = createStrategy.xAppNameHeader(name);
        }
        if (version != null) {
            createStrategy = createStrategy.xAppVersionHeader(version);
        }
        if (platform != null) {
            createStrategy = createStrategy.xPlatformHeader(platform);
        }
        String execute = createStrategy.execute(ResponseBodyData::asString);
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        JSONObject jsonObject = new JSONObject(execute);
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
//        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("266603")
    @DisplayName("C266603.CreateStrategy. Валидация запроса: siebelId не передан в заголовке с key = 'X-TCS-SIEBEL-ID'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266603()  {
        String title = "Тест стратегия CreateStrategy Autotest 002";
        String description = "New test стратегия Autotest 002";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title,basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(401));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("438058")
    @DisplayName("C438058.CreateStrategy. Валидация запроса: siebelId = '' в заголовке с key = 'X-TCS-SIEBEL-ID'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C438058() {
        String title = "Тест стратегия CreateStrategy Autotest 003";
        String description = "New test стратегия Autotest 003";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title,basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader("")
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    private static Stream<Arguments> provideStringsForBodyCreateStrategy() {
        return Stream.of(
            Arguments.of(null, StrategyRiskProfile.CONSERVATIVE, "Autotest", "days"),
            Arguments.of(Currency.RUB, null, "Autotest", "days"),
            Arguments.of(Currency.RUB, StrategyRiskProfile.CONSERVATIVE, null, "days"),
            Arguments.of(Currency.RUB, StrategyRiskProfile.CONSERVATIVE, "Autotest", null)
        );
    }
    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForBodyCreateStrategy")
    @AllureId("443467")
    @DisplayName("C443467.CreateStrategy. Валидация запроса: обязательные входные параметры в body: " +
        "baseCurrency, riskProfile, title, positionRetentionId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C443467(Currency baseCurrency, StrategyRiskProfile strategyRiskProfile, String title, String positionRetentionId) {
        String description = "New test стратегия Autotest 004";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal baseMoney = new BigDecimal("8000");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (baseCurrency, contractId, description,
            strategyRiskProfile, title,baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("499682")
    @DisplayName("C499682.CreateStrategy. Валидация запроса: обязательный параметр в body: contractId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C499682() {
        String title = "Тест стратегия CreateStrategy Autotest 005";
        String description = "New test стратегия Autotest 005";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(Currency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        createStrategyRequest.setPositionRetentionId("days");
//        createStrategyRequest.setFeeRate(feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("441591")
    @DisplayName("C441591.CreateStrategy. Валидация запроса: title < 1 символа")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441591() {
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "";
        String description = "New test стратегия Autotest 006";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        assertThat("errorCode != Error", expectedResponse.getBody().jsonPath().get("errorCode"), equalTo("Error"));
        assertThat("errorMessage != Сервис временно недоступен", expectedResponse.getBody().jsonPath().get("errorMessage"), equalTo("Сервис временно недоступен"));
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("441407")
    @DisplayName("C441407.CreateStrategy. Валидация запроса description < 1 символа")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441407() {
        String title = "Тест стратегия CreateStrategy Autotest 007";
        String description = "";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("455499")
    @DisplayName("C455499.CreateStrategy. Валидация запроса: длина SiebelId превышает 12 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C455499() {
        String title = "Тест стратегия CreateStrategy Autotest 008";
        String description = "New test стратегия Autotest 008";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xTcsSiebelIdHeader("1-BJ81HL9DDYH")
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("271533")
    @DisplayName("C271533.CreateStrategy. clientId = NULL в ClientIdCache(несуществующее значение)")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C271533() throws JSONException {
        String title = "CreateStrategy Autotest 009";
        String description = "New test стратегия Autotest 009";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader("2-BJ81HL9")
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Invest account not found не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @ParameterizedTest
    @EnumSource(value = ClientStatusType.class, names = {"none", "confirmed"})
    @AllureId("266604")
    @DisplayName("C266604.CreateStrategy. Клиент не зарегистрирован как ведущий, client.master_status != 'registered'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266604(ClientStatusType clientStatusType) throws JSONException {
        String title = "CreateStrategy Autotest 010";
        String description = "New test стратегия Autotest 010";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, clientStatusType);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Registered client not found не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("266605")
    @DisplayName("C266605.CreateStrategy. Тип договора клиента != 'broker'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266605()  {
        //Находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelId = billingService.getFindNotBrokerAccountBySiebelId(siebelIdNotBroker);
        UUID investIdNotBroker = findValidAccountWithSiebelId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebelId.get(0).getId();
        String title = "CreateStrategy Autotest 011";
        String description = "New test стратегия Autotest 011";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Создаем по нему запись в tracking.client
        createClientNotBrokerOrOpened(investIdNotBroker, siebelIdNotBroker, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(siebelIdNotBroker)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Registered client not found не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("266606")
    @DisplayName("C266606.CreateStrategy. Статус договора клиента != 'opened'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266606() throws JSONException {
        //Находим клиента со статусом договора !=opened
        //profile = profileService.getProfileBySiebelId(siebelIdNotOpen);
        //Находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelId = billingService.getFindNotOpenAccountBySiebelId(siebelIdNotOpen);
        UUID investIdNotOpen = findValidAccountWithSiebelId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebelId.get(0).getId();
        String title = "CreateStrategy Autotest 012";
        String description = "New test стратегия Autotest 012";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Создаем по нему запись в tracking.client
        createClientNotBrokerOrOpened(investIdNotOpen, siebelIdNotOpen, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(siebelIdNotOpen)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Registered client not found не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("266607")
    @DisplayName("C266607.CreateStrategy. Договор не соответствует клиенту")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266607() {
        String title = "CreateStrategy Autotest 013";
        String description = "New test стратегия Autotest 013";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим 2 клиента в сервисе счетов и Создаем запись o БД автоследование(db-tracking.trading.local) в табл. client Для 1 клиента
        List<BrokerAccount> brokerAccounts = billingService.getFindTwoValidContract();
        client = clientService.createClient(brokerAccounts.get(0).getInvestAccount().getId(), ClientStatusType.registered, null, null);
        //Вызываем метод  GetUntrackedContracts с siebelId от первого клиента и номер договора от второго клиента
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, brokerAccounts.get(1).getId(), description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(brokerAccounts.get(0).getInvestAccount().getSiebelId())
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Registered client not found не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOptFirst = contractService.findContract(brokerAccounts.get(0).getId());
        assertThat("запись по договору не равно", contractOptFirst.isPresent(), is(false));
        Optional<Contract> contractOptSecond = contractService.findContract(brokerAccounts.get(1).getId());
        assertThat("запись по договору не равно", contractOptSecond.isPresent(), is(false));
        Optional<Strategy> strategyOptFirst = strategyService.findStrategyByContractId(brokerAccounts.get(0).getId());
        assertThat("запись по стратегии не равно", strategyOptFirst.isPresent(), is(false));
        Optional<Strategy> strategyOptSecond = strategyService.findStrategyByContractId(brokerAccounts.get(1).getId());
        assertThat("запись по стратегии не равно", strategyOptSecond.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("441659")
    @DisplayName("C441659.CreateStrategy. Некорректно передан параметр contractId в теле запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441659() {
        String title = "Тест стратегия CreateStrategy Autotest 014";
        String description = "New test стратегия Autotest 014";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        String contractIdNew = contractId + "123";
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractIdNew, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = contractService.findContract(contractIdNew);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }



    @SneakyThrows
    @Test
    @AllureId("638559")
    @DisplayName("C638559.CreateStrategy. Валидация запроса: не передан параметр baseMoneyPositionQuantity")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C638559() {
        String title = "Тест стратегия CreateStrategy Autotest 015";
        String description = "New test стратегия Autotest 015";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(Currency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setPositionRetentionId("days");
//        createStrategyRequest.setFeeRate(feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("C931443")
    @DisplayName("C931443.CreateStrategy. Создание стратегии с недопустимым значением positionRetentionId = 'years'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C931443() {
        String title = "CreateStrategy Autotest 016";
        String description = "New test стратегия Autotest 016";
        String positionRetentionId = "years";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим данные по клиенту чере API-сервиса счетов, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("C931444")
    @DisplayName("C931444.CreateStrategy. Валидация запроса: не передан параметр positionRetentionId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C931444() {
        String title = "Тест стратегия CreateStrategy Autotest 016";
        String description = "New test стратегия Autotest 016";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(0.04);
//        feeRate.setResult(0.2);
        //Находим данные по клиенту чере API-сервиса счетов, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);

        //Формируем body для запроса
        BigDecimal basemoney = new BigDecimal("1000.0");
        CreateStrategyRequest createStrategyRequest = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, basemoney, null, feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    private static Stream<Arguments> provideFeeRateMultiplicity() {
        return Stream.of(
            Arguments.of(0.0369, 0.2, "Ставка комиссии за управление должна быть кратна 0.001"),
            Arguments.of(0.04, 0.1294, "Ставка комиссии за результат должна быть кратна 0.001")
        );
    }
    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideFeeRateMultiplicity")
    @AllureId("1050967")
    @DisplayName("C1050967.CreateStrategy.Проверка на кратность ставок по комиссии")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C1050967(double management, double result, String errorMessage) {
        String title = "Тест стратегия CreateStrategy Autotest 017";
        String description = "New test стратегия Autotest 017";
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(management);
//        feeRate.setResult(result);
        //Находим данные по клиенту чере API-сервиса счетов, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessageReq = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is(errorMessageReq));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    private static Stream<Arguments> provideFeeRateLowerUpper() {
        return Stream.of(
            Arguments.of(0.009, 0.2, "Ставка комиссии за управление должна быть в промежутке от 0.01 до 0.1"),
            Arguments.of(0.14, 0.2, "Ставка комиссии за управление должна быть в промежутке от 0.01 до 0.1"),
            Arguments.of(0.04, 0.05, "Ставка комиссии за результат должна быть в промежутке от 0.1 до 0.5"),
            Arguments.of(0.04, 0.6, "Ставка комиссии за результат должна быть в промежутке от 0.1 до 0.5")
        );
    }
    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideFeeRateLowerUpper")
    @AllureId("1051229")
    @DisplayName("C1051229.CreateStrategy.Проверка на попадание ставок по комиссии в установленный промежуток")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C1051229(double management, double result, String errorMessage) {
        String title = "Тест стратегия CreateStrategy Autotest 018";
        String description = "New test стратегия Autotest 018";
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(management);
//        feeRate.setResult(result);
        //Находим данные по клиенту чере API-сервиса счетов, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessageReq = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is(errorMessageReq));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    private static Stream<Arguments> provideBodyFeeRateCreateStrategy() {
        return Stream.of(
            Arguments.of(null, 0.2),
            Arguments.of(0.04, null)
        );
    }
    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideBodyFeeRateCreateStrategy")
    @AllureId("1051233")
    @DisplayName("C1051233.CreateStrategy.Валидация запроса: обязательный параметр в body: feeRate")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C1051233(Double management, Double result) {
        String title = "Тест стратегия CreateStrategy Autotest 018";
        String description = "New test стратегия Autotest 018";
        String positionRetentionId = "days";
        //ToDo feeRate was disabled
        String feeRate = "disabled";
//        StrategyFeeRate feeRate = new StrategyFeeRate();
//        feeRate.setManagement(management);
//        feeRate.setResult(result);
        //Находим подходящего клиента в сервисе счетов и БД social, Создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //Формируем body для запроса
        BigDecimal baseMoney = new BigDecimal("15000.0");
        CreateStrategyRequest request = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, positionRetentionId, feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //Проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создает запись по нему в таблице tracking.client
    public String createClient(String SIEBEL_ID, ClientStatusType clientIdStatusType) {
        GetBrokerAccountsResponse resBrokerAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery("false")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resBrokerAccount.getInvestId();
        String contractId = resBrokerAccount.getBrokerAccounts().get(0).getId();
        client = clientService.createClient(investId, clientIdStatusType, null, null);
        return contractId;
    }


    //Метод находит подходящий siebelId в сервисе счетов и Создает запись по нему в таблице tracking.client
    public void createClientNotBrokerOrOpened(UUID investId, String SIEBEL_ID, ClientStatusType clientIdStatusType) {
        //Находим данные по клиенту в БД social
        //profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        //createClient(investId, ClientStatusType.registered, null);
        client = clientService.createClient(investId, clientIdStatusType, null, null);
    }

    //ToDo Пока отключили fee, нужно будет вернуть StrategyFeeRate   feeRate, а не string
    CreateStrategyRequest createStrategyRequest (Currency currency, String contractId, String description,
                                                 StrategyRiskProfile strategyRiskProfile, String title,
                                                 BigDecimal basemoney, String  positionRetentionId,
                                                 String   feeRate) {
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
