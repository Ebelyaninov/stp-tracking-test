package stpTrackingApi.createStrategy;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest;
import ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;


@Epic("createStrategy - Создание стратегии")
@Feature("TAP-6805")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class})
public class CreateStrategyErrorTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService сontractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ProfileService profileService;

    Client client;
    Profile profile;
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();;
    String SIEBEL_ID = "1-5RLRHAS";
    String siebelIdNotOpen = "5-YE3B7BWM";
    String siebelIdNotBroker = "5-1BKTNCQL4";

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

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCreateStrategy")
    @AllureId("442935")
    @DisplayName("C442935.CreateStrategy.Валидация запроса: обязательные входные параметры в headers: X-APP-NAME, X-APP-VERSION, X-APP-PLATFORM")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C442935(String name, String version, String platform) {
        String title = "тест стратегия CreateStrategy001";
        String description = "new test стратегия autotest001";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        createStrategy.execute(ResponseBodyData::asString);
        assertThat("номера стратегии не равно", createStrategy.execute(ResponseBodyData::asString).substring(57, 98), is("errorMessage\":\"Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("266603")
    @DisplayName("C266603.CreateStrategy.Валидация запроса: siebelId не передан в заголовке с key = 'X-TCS-SIEBEL-ID'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266603() {
        String title = "тест стратегия CreateStrategy002";
        String description = "new test стратегия autotest002";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy =  strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(401));
        createStrategy.execute(ResponseBodyData::asString);
        assertThat("номера стратегии не равно", createStrategy.execute(ResponseBodyData::asString).substring(57, 89), is("errorMessage\":\"Недостаточно прав"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("438058")
    @DisplayName("C438058.CreateStrategy.Валидация запроса: siebelId = '' в заголовке с key = 'X-TCS-SIEBEL-ID'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C438058() {
        String title = "тест стратегия CreateStrategy003";
        String description = "new test стратегия autotest003";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy =  strategyApi.createStrategy()
            .xTcsSiebelIdHeader("")
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        assertThat("номера стратегии не равно", createStrategy.execute(ResponseBodyData::asString).substring(57, 98), is("errorMessage\":\"Сервис временно недоступен"));
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    private static Stream<Arguments> provideStringsForBodyCreateStrategy() {
        return Stream.of(
            Arguments.of(null, StrategyRiskProfile.CONSERVATIVE, "test"),
            Arguments.of(StrategyBaseCurrency.RUB, null, "test"),
            Arguments.of(StrategyBaseCurrency.RUB, StrategyRiskProfile.CONSERVATIVE, null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForBodyCreateStrategy")
    @AllureId("443467")
    @DisplayName("C443467.CreateStrategy.Валидация запроса: обязательные входные параметры в body: contractId, baseCurrency, riskProfile, title")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C443467(StrategyBaseCurrency baseCurrency, StrategyRiskProfile strategyRiskProfile, String title) {
        String description = "new test стратегия autotest004";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(baseCurrency);
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(strategyRiskProfile);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy =  strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        assertThat("номера стратегии не равно", createStrategy.execute(ResponseBodyData::asString).substring(57, 98), is("errorMessage\":\"Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("499682")
    @DisplayName("C499682.CreateStrategy.Валидация запроса: обязательный параметр в body: contractId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C499682() {
        String title = "тест стратегия CreateStrategy005";
        String description = "new test стратегия autotest005";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy =  strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        assertThat("номера стратегии не равно", createStrategy.execute(ResponseBodyData::asString).substring(57, 98), is("errorMessage\":\"Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("441591")
    @DisplayName("C441591.CreateStrategy.Валидация запроса: title < 1 символа")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441591() {
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "";
        String description = "new test стратегия autotest CreateStrategy006";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
//        assertThat("x-server-time не равно", expectedResponse.getHeaders().getValue("x-server-time").substring(0, 16), is(dateNow));
        //находим в БД автоследования созданный контракт и проверяем его поля
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("441407")
    @DisplayName("C441407.CreateStrategy.Валидация запроса description < 1 символa")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441407() {
        String title = "тест стратегия CreateStrategy007";
        String description = "";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("455499")
    @DisplayName("C455499.CreateStrategy.Валидация запроса: SiebleId передан более 12 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C455499() {
        String title = "тест стратегия CreateStrategy008";
        String description = "new test стратегия autotest CreateStrategy008";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("271533")
    @DisplayName("C271533.CreateStrategy.clientId = NULL в ClientIdCache(несуществующие значение)")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C271533() throws JSONException {
        String title = "тест стратегия CreateStrategy009";
        String description = "new test стратегия autotest CreateStrategy009";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        assertThat("код ошибки Invest account not found не равно", errorCode, is("0350-02-B04"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @ParameterizedTest
    @EnumSource(value = ClientStatusType.class, names = {"none", "confirmed"})
    @AllureId("266604")
    @DisplayName("C266604.CreateStrategy.Клиент не зарегистрирован как ведущий, client.master_status != 'registered'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266604(ClientStatusType clientStatusType) throws JSONException {
        String title = "тест стратегия CreateStrategy010";
        String description = "new test стратегия autotest CreateStrategy010";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, clientStatusType);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        assertThat("код ошибки Registered client not found не равно", errorCode, is("0350-02-B02"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("266605")
    @DisplayName("C266605.CreateStrategy.Тип договора клиента != 'broker'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266605() throws JSONException {
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindNotBrokerAccountBySiebelId(siebelIdNotBroker);
        UUID investIdNotBroker = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        String title = "тест стратегия CreateStrategy010";
        String description = "new test стратегия autotest CreateStrategy010";
        //создаем по нему запись в tracking.client
        createClientNotBrokerOrOpened(investIdNotBroker, siebelIdNotBroker, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        assertThat("код ошибки Registered client not found не равно", errorCode, is("0350-02-V02"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("266606")
    @DisplayName("C266606.CreateStrategy.Статус договора клиента != 'opened'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266606() throws JSONException {
        //находим клиента со статусом договора !=opened
        profile = profileService.getProfileBySiebelId(siebelIdNotOpen);
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindNotOpenAccountBySiebelId(siebelIdNotOpen);
        UUID investIdNotOpen = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        String title = "тест стратегия CreateStrategy011";
        String description = "new test стратегия autotest CreateStrategy011";
        //создаем по нему запись в tracking.client
        createClientNotBrokerOrOpened(investIdNotOpen, siebelIdNotOpen, ClientStatusType.registered);
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        assertThat("код ошибки Registered client not found не равно", errorCode, is("0350-02-V03"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("266607")
    @DisplayName("C266607.CreateStrategy.Договор не соответствует клиенту")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C266607() throws JSONException {
        String title = "тест стратегия CreateStrategy12";
        String description = "new test стратегия12";
        //находим 2 клиента в сервисе счетов и Создаем запись o БД автоследование(db-tracking.trading.local) в табл. client Для 1 клиента
        List<BrokerAccount> brokerAccounts = billingService.getFindTwoValidContract();
        client = clientService.createClient(brokerAccounts.get(0).getInvestAccount().getId(), ClientStatusType.registered, null);
        //вызываем метод  GetUntrackedContracts с siebelId от первого клиента и номер договора от второго клиента
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(brokerAccounts.get(1).getId());
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
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
        assertThat("код ошибки Registered client not found не равно", errorCode, is("0350-02-V01"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractOptFirst = сontractService.findContract(brokerAccounts.get(0).getId());
        assertThat("запись по договору не равно", contractOptFirst.isPresent(), is(false));
        Optional<Contract> contractOptSecond = сontractService.findContract(brokerAccounts.get(1).getId());
        assertThat("запись по договору не равно", contractOptSecond.isPresent(), is(false));
        Optional<Strategy> strategyOptFirst = strategyService.findStrategyByContractId(brokerAccounts.get(0).getId());
        assertThat("запись по стратегии не равно", strategyOptFirst.isPresent(), is(false));
        Optional<Strategy> strategyOptSecond = strategyService.findStrategyByContractId(brokerAccounts.get(1).getId());
        assertThat("запись по стратегии не равно", strategyOptSecond.isPresent(), is(false));
    }


    @Test
    @AllureId("441659")
    @DisplayName("C441659.CreateStrategy.Некорректно передан параметр contractId в теле запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C441659() {
        String title = "тест стратегия CreateStrategy13";
        String description = "new test стратегия autotest CreateStrategy13";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        String contractIdNew = contractId + "123";
        //форируем body для запроса
        BigDecimal basemoney = new BigDecimal("8000.0");
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractIdNew);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        //вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = сontractService.findContract(contractIdNew);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("638559")
    @DisplayName("C638559.CreateStrategy.Валидация запроса: не передан параметр baseMoneyPositionQuantity\n")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C638559() {
        String title = "тест стратегия CreateStrategy15";
        String description = "new test стратегия autotest CreateStrategy15";
        //находим подходящего клиента в сервисе счетов и БД social, создаем по нему запись в tracking.client
        String contractId = createClient(SIEBEL_ID, ClientStatusType.registered);
        //форируем body для запроса
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setBaseCurrency(StrategyBaseCurrency.RUB);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(StrategyRiskProfile.CONSERVATIVE);
        createStrategyRequest.setTitle(title);
        //вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(createStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        createStrategy.execute(ResponseBodyData::asString);
        //проверяем код ответа и что записи в БД автоследование в tracking.contract tracking.strategy отсутствуют
        Optional<Contract> contractNewOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractNewOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = сontractService.findContract(contractId);
        assertThat("запись по договору не равно", contractOpt.isPresent(), is(false));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    //*** Методы для работы тестов ***
    //метод находит подходящий siebelId в сервисе счетов и создаем запись по нему в табл. tracking.client
    public String createClient(String SIEBEL_ID, ClientStatusType clientIdStatusType) {
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        client = clientService.createClient(investId, clientIdStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()));
        return contractId;
    }


    //метод находит подходящий siebelId в сервисе счетов и создаем запись по нему в табл. tracking.client
    public void createClientNotBrokerOrOpened(UUID investId, String SIEBEL_ID, ClientStatusType clientIdStatusType) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        client = clientService.createClient(investId, clientIdStatusType, null);
    }
}
