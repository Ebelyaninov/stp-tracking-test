package stpTrackingApi.createSignal;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.model.KafkaModelFiregInstrumentCoCaNoCurr;
import ru.qa.tinkoff.kafka.model.KafkaModelFiregInstrumentCoCaNoType;
import ru.qa.tinkoff.kafka.model.KafkaModelFiregInstrumentCocaColaEvent;
import ru.qa.tinkoff.kafka.model.KafkaModelFiregInstrumentWayfairWithRiskEvent;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateSignalRequest;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("createSignal - Создание торгового сигнала")
@Feature("TAP-8619")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class
})
public class CreateSignalErrorTest {
    @Autowired
    StringSenderService stringSenderService;
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
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    StpTrackingApiSteps steps;

    ExchangePositionApi exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;
    SignalApi signalApi = ApiClient.api(ApiClient.Config.apiConfig()).signal();

    String contractId;
    UUID strategyId;
    String ticker = "XS0587031096";
    String tradingClearingAccount = "TKCBM_TCAB";

    String SIEBEL_ID = "1-3W70RM8";
    String contractIdMaster = "2041774643";
    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(steps.strategyMaster);
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
                masterPortfolioDao.deleteMasterPortfolio(contractId, strategyId);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideStringsForHeadersCreateSignal() {
        return Stream.of(
            Arguments.of(null, "android", "4.5.6"),
            Arguments.of("trading-invest", null, "I.3.7"),
            Arguments.of("trading", "ios 8.1", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCreateSignal")
    @AllureId("653780")
    @DisplayName("C653780.CreateSignal.Валидация запроса: x-app-name, x-app-version, x-platform")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C653780(String appName, String appVersion, String appPlatform) {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (appName != null) {
            createSignal = createSignal.xAppNameHeader(appName);
        }
        if (appVersion != null) {
            createSignal = createSignal.xAppVersionHeader(appVersion);
        }
        if (appPlatform != null) {
            createSignal = createSignal.xPlatformHeader(appPlatform);
        }
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("655896")
    @DisplayName("C655896.CreateSignal.Валидация запроса: не передан заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C655896() {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(401));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }



    private static Stream<Arguments> provideStringsForBodyCreateSignal() {
        return Stream.of(
            Arguments.of(null,  new BigDecimal("10.0"), 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
//            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", null, 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, new BigDecimal("10.0"), null, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, new BigDecimal("10.0"), 4, null, "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, new BigDecimal("10.0"), 4, UUID.randomUUID(), null, "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, new BigDecimal("10.0"), 4, UUID.randomUUID(), "XS0587031096", null, 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, new BigDecimal("10.0"), 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForBodyCreateSignal")
    @AllureId("656034")
    @DisplayName("C656034.CreateSignal.Валидация запроса: contractId, strategyId, version, ticker, tradingClearingAccount, action, quantity, price")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656034(CreateSignalRequest.ActionEnum action, BigDecimal price, Integer quantityRequest,
                 UUID strategyIdTest, String ticker, String tradingClearingAccount, Integer version) {
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(action);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyIdTest);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));

    }


    private static Stream<Arguments> provideStringsForSiebelIdCreateSignal() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("1-A8TZDT212345"));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForSiebelIdCreateSignal")
    @AllureId("655947")
    @DisplayName("C655947.CreateSignal.Валидация запроса: заголовок x-tcs-siebel-id, количество символов > 12 и < 1")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C655947(String siebelId) {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(siebelId)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    private static Stream<Arguments> provideStringsForTickerCreateSignal() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("XS05870310965464"));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForTickerCreateSignal")
    @AllureId("656466")
    @DisplayName("C656466.CreateSignal.Валидация запроса: параметр ticker, количество символов > 12 и < 1")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656466(String ticker) {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    private static Stream<Arguments> provideStringsForTradingClearingAccountCreateSignal() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("L01+00000SPB5353"));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForTradingClearingAccountCreateSignal")
    @AllureId("656470")
    @DisplayName("C656470.CreateSignal.Валидация запроса: параметр tradingClearingAccount, количество символов > 12 и < 1")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656470(String tradingClearingAccount) {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }




    private static Stream<Arguments> provideStringsForPriceCreateSignal() {
        return Stream.of(
            Arguments.of(new BigDecimal("0.0")),
            Arguments.of(new BigDecimal("-10.123")));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForPriceCreateSignal")
    @AllureId("656271")
    @DisplayName("C656271.CreateSignal.Валидация запроса: значение параметра price 0, отрицательное значение")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656271(BigDecimal price) {
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("656481")
    @DisplayName("C656481.CreateSignal.Не удалось получить clientId из кэше clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656481() {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader("2-A8TZDT2")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("656494")
    @DisplayName("C656494.CreateSignal.ClientId, найденный в clientIdCache, <> contract.client_id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656494() {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        String contractOther = contractService.findOneContract().get().getId();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }



    @SneakyThrows
    @Test
    @AllureId("656557")
    @DisplayName("C656557.CreateSignal.Стратегия strategyId не существует")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656557() {
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("657123")
    @DisplayName("C657123.CreateSignal.Не найдена запись в таблице master_potfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657123() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 1;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("657138")
    @DisplayName("C657138.CreateSignal.Version из запроса != master_portfolio.version найденного портфеля")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657138() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 5;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, 3, "3556.78", date);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Виртуальный портфель устарел"));
    }


    @SneakyThrows
    @Test
    @AllureId("657144")
    @DisplayName("C657144.CreateSignal.Позиция не найдена в кэше trackingExchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657144() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 5;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, "GMKN1022", "NDS000000001", version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("657162")
    @DisplayName("C657162.CreateSignal.Pасписание не найдено в кэше exchangeTradingScheduleCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657162() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 5;
        String ticker = "TEST";
        String tradingClearingAccount = "TEST";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Торговая площадка не работает"));
    }


    @SneakyThrows
    @Test
    @AllureId("657198")
    @DisplayName("C657198.CreateSignal.Позиция не найдена в кэше exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657198() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 5;
        String ticker = "MTS_TEST";
        String tradingClearingAccount = "L01+00000SPB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("656524")
    @DisplayName("C656524.CreateSignal.Биржа не работает")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656524() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 5;
        String ticker = "XS0743596040";
        String tradingClearingAccount = "NDS000000001";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.MOEX, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }



    @SneakyThrows
    @Test
    @AllureId("657204")
    @DisplayName("C657204.CreateSignal.Quantity из запроса / значение lot из exchangePositionCache != целое число")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657204() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 3;
        int version = 4;
        String ticker = "XS0424860947";
        String tradingClearingAccount = "L01+00002F00";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Необходимо указать целое число лотов"));
    }

    @SneakyThrows
    @Test
    @AllureId("657314")
    @DisplayName("C657314.CreateSignal.Полученное значение quantity денежной позиции < 0, action = 'buy'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657314() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("1000.0");
        int quantityRequest = 6;
        int version = 2;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно денег в портфеле для увеличения позиции"));
    }


    @SneakyThrows
    @Test
    @AllureId("657490")
    @DisplayName("C657490.CreateSignal.Позиция не найдена в master_portfolio.position, action = 'sell'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657490() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 16;
        int version = 2;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно актива в портфеле для уменьшения позиции"));
    }


    @SneakyThrows
    @Test
    @AllureId("657523")
    @DisplayName("C657523.CreateSignal.Quantity позиции из запроса > master_portfolio_position.quantity, action = 'sell'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657523() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 30;
        int version = 2;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно актива в портфеле для уменьшения позиции"));
    }


    @SneakyThrows
    @Test
    @AllureId("657577")
    @DisplayName("C657577.CreateSignal.Позиция не доступна для автоследования, tracking_allowed = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657577() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 3;
        int version = 2;
        String ticker = "MTS0620";
        String tradingClearingAccount = "TKCBM_TCAB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, false, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("658177")
    @DisplayName("C658177.CreateSignalНе совпадает валюта стратегии и позиции, значение currency из кэша != strategy.base_currency")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C658177() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 30;
        int version = 2;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }


//    @SneakyThrows
//    @Test
//    @AllureId("658142")
//    @DisplayName("C658142.CreateSignal.У позиции отсутствует один из необходимых параметров (type, currency) в exchangePositionCache")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
//    void C658142() {
//        int randomNumber = 0 + (int) (Math.random() * 100);
//        String title = "Autotest" +String.valueOf(randomNumber);
//        String description = "new test стратегия autotest";
//        BigDecimal price = new BigDecimal("10.0");
//        int quantityRequest = 3;
//        int version = 4;
//        String ticker = "KO";
//        String tradingClearingAccount = "L01+00000SPB";
//        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
//        log.info("Получаем локальное время: {}", now);
//        //отправляем событие в fireg.instrument
//        String event = KafkaModelFiregInstrumentCoCaNoType.getKafkaTemplate(LocalDateTime.now());
//        String key = "BBG000BMX289";
//        //отправляем событие в топик kafka social.event
//        stringSenderService.send(Topics.FIREG_INSTRUMENT, key, event);
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.randomUUID();
//        //создаем в БД tracking стратегию на ведущего
//        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
//            "12");
//        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
//        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
//        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
//        //формируем тело запроса метода CreateSignal
//        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
//            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
//        // вызываем метод CreateSignal
//        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xDeviceIdHeader("new")
//            .xTcsSiebelIdHeader(SIEBEL_ID)
//            .body(request)
//            .respSpec(spec -> spec.expectStatusCode(422));
//        createSignal.execute(ResponseBodyData::asString);
//        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
//        String errorCode = jsonObject.getString("errorCode");
//        String errorMessage = jsonObject.getString("errorMessage");
//        assertThat("код ошибки не равно", errorCode, is("Error"));
//        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
//    }
//
//
//    @SneakyThrows
//    @Test
//    @AllureId("658142")
//    @DisplayName("C658142.CreateSignal.У позиции отсутствует один из необходимых параметров (type, currency) в exchangePositionCache")
//    @Subfeature("Альтернативные сценарии")
//    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
//    void C658142_1() {
//        int randomNumber = 0 + (int) (Math.random() * 100);
//        String title = "Autotest" +String.valueOf(randomNumber);
//        String description = "new test стратегия autotest";
//        BigDecimal price = new BigDecimal("10.0");
//        int quantityRequest = 3;
//        int version = 4;
//        String ticker = "KO";
//        String tradingClearingAccount = "L01+00000SPB";
//        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
//        log.info("Получаем локальное время: {}", now);
//        //отправляем событие в fireg.instrument
//        String event = KafkaModelFiregInstrumentCoCaNoCurr.getKafkaTemplate(LocalDateTime.now());
//        String key = "BBG000BMX289";
//        //отправляем событие в топик kafka social.event
//        stringSenderService.send(Topics.FIREG_INSTRUMENT, key, event);
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.randomUUID();
//        //создаем в БД tracking стратегию на ведущего
//        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
//            StrategyStatus.active, 0, LocalDateTime.now());
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
//            "12");
//        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
//        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
//        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
//        //формируем тело запроса метода CreateSignal
//        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
//            price, quantityRequest, strategyId, ticker, tradingClearingAccount, version);
//        // вызываем метод CreateSignal
//        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xDeviceIdHeader("new")
//            .xTcsSiebelIdHeader(SIEBEL_ID)
//            .body(request)
//            .respSpec(spec -> spec.expectStatusCode(422));
//        createSignal.execute(ResponseBodyData::asString);
//        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
//        String errorCode = jsonObject.getString("errorCode");
//        String errorMessage = jsonObject.getString("errorMessage");
//        assertThat("код ошибки не равно", errorCode, is("Error"));
//        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
//    }




    private static Stream<Arguments> provideRiskLevelError() {
        return Stream.of(
//            Arguments.of("0", StrategyRiskProfile.conservative),
//            Arguments.of("0", StrategyRiskProfile.moderate),
//            Arguments.of("1", StrategyRiskProfile.conservative)
//            XS0587031096
            Arguments.of("LEVI", "TKCBM_TCAB", "26.3", StrategyRiskProfile.conservative),
            Arguments.of("XS0587031096", "TKCBM_TCAB", "97.7", StrategyRiskProfile.conservative),
            Arguments.of("XS0587031096", "TKCBM_TCAB", "97.7", StrategyRiskProfile.moderate)

        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRiskLevelError")
    @AllureId("658178")
    @DisplayName("C658178.CreateSignal.Риск-профиль позиции превышает риск-профиль стратегии")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C658178(String ticker, String tradingClearingAccount, String price, StrategyRiskProfile strategyRiskProfile) {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        int quantityRequest = 3;
        int version = 4;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, strategyRiskProfile,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "12");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "3556.78", date);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            new BigDecimal(price), quantityRequest, strategyId, ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }


    //*** Методы для работы тестов ***

    //метод находит подходящий siebelId в сервисе счетов и создаем запись по нему в табл. tracking.client
    void getExchangePosition(String ticker, String tradingClearingAccount, ExchangePosition.ExchangeEnum exchange,
                             Boolean trackingAllowed, Integer dailyQuantityLimit) {
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        if (exchangePositionOpt.isPresent() == false) {
            List<OrderQuantityLimit> orderQuantityLimitList
                = new ArrayList<>();
            OrderQuantityLimit orderQuantityLimit = new OrderQuantityLimit();
            orderQuantityLimit.setLimit(1000);
            orderQuantityLimit.setPeriodId("additional_liquidity");
            orderQuantityLimitList.add(orderQuantityLimit);
            //формируем тело запроса
            CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
            createExPosition.exchange(exchange);
            createExPosition.dailyQuantityLimit(dailyQuantityLimit);
            createExPosition.setOrderQuantityLimits(orderQuantityLimitList);
            createExPosition.setTicker(ticker);
            createExPosition.setTrackingAllowed(trackingAllowed);
            createExPosition.setTradingClearingAccount(tradingClearingAccount);
            //вызываем метод createExchangePosition
            exchangePositionApi.createExchangePosition()
                .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("android")
                .xDeviceIdHeader("test")
                .xTcsLoginHeader("tracking_admin")
                .body(createExPosition)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse.class));
        }
    }


    public CreateSignalRequest createSignalRequest(CreateSignalRequest.ActionEnum actionEnum, BigDecimal price,
                                                   int quantityRequest, UUID strategyId, String ticker,
                                                   String tradingClearingAccount, int version) {
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(actionEnum);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
        return request;
    }

}
