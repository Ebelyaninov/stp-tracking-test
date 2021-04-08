package stpTrackingApi.createSignal;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateSignalRequest;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
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
    KafkaAutoConfiguration.class})
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

    ExchangePositionApi exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    SignalApi signalApi = ApiClient.api(ApiClient.Config.apiConfig()).signal();
    Client client;
    Contract contract;
    Strategy strategy;
    String contractId;
    UUID strategyId;
    String ticker = "XS0587031096";
    String tradingClearingAccount = "L01+00000SPB";
    String SIEBEL_ID = "1-3W70RM8";

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
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C653780(String appName, String appVersion, String appPlatform) {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
//       //формируем тело запроса метода CreateSignal
        var request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
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
    }


    @SneakyThrows
    @Test
    @AllureId("655896")
    @DisplayName("C655896.CreateSignal.Валидация запроса: не передан заголовок x-tcs-siebel-id")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C655896() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        var request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(401));
        createSignal.execute(ResponseBodyData::asString);
    }


    private static Stream<Arguments> provideStringsForBodyCreateSignal() {
        return Stream.of(
            Arguments.of(null, "2000356465", 10.0, 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, null, 10.0, 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", null, 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", 10.0, null, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", 10.0, 4, null, "XS0587031096", "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", 10.0, 4, UUID.randomUUID(), null, "L01+00000SPB", 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", 10.0, 4, UUID.randomUUID(), "XS0587031096", null, 1),
            Arguments.of(CreateSignalRequest.ActionEnum.SELL, "2000356465", 10.0, 4, UUID.randomUUID(), "XS0587031096", "L01+00000SPB", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForBodyCreateSignal")
    @AllureId("656034")
    @DisplayName("C656034.CreateSignal.Валидация запроса: contractId, strategyId, version, ticker, tradingClearingAccount, action, quantity, price")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656034(CreateSignalRequest.ActionEnum action, String contractIdTest, Double price, Integer quantityRequest,
                 UUID strategyIdTest, String ticker, String tradingClearingAccount, Integer version) {
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(action);
        request.setContractId(contractIdTest);
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
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C655947(String siebelId) {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
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
    }


    private static Stream<Arguments> provideStringsForContractIdCreateSignal() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("20003564654353"));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForContractIdCreateSignal")
    @AllureId("656238")
    @DisplayName("C656238.CreateSignal.Валидация запроса: параметр contractId, количество символов > 10 и < 1")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656238(String contract) {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contract);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656466(String ticker) {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656470(String tradingClearingAccount) {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
    }

    private static Stream<Arguments> provideStringsForPriceCreateSignal() {
        return Stream.of(
            Arguments.of(0.0),
            Arguments.of(-10.123));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForPriceCreateSignal")
    @AllureId("656271")
    @DisplayName("C656271.CreateSignal.Валидация запроса: значение параметра price 0, отрицательное значение")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656271(Double price) {
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
    }

    @SneakyThrows
    @Test
    @AllureId("656481")
    @DisplayName("C656481.CreateSignal.Не удалось получить clientId из кэше clientIdCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656481() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
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
        assertThat("код ошибки ClientId not found не равно", errorCode, is("0350-06-B13"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("656494")
    @DisplayName("C656494.CreateSignal.ClientId, найденный в clientIdCache, <> contract.client_id")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656494() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        String contractOther = contractService.findOneContract().get().getId();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractOther);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect clientId не равно", errorCode, is("0350-06-B14"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("656513")
    @DisplayName("C656513.CreateSignal.Не существующее значение contractId")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656513() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId("1000356465");
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Contract not found не равно", errorCode, is("0350-06-B08"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("656553")
    @DisplayName("C656553.CreateSignal.Стратегия strategyId не соответствует договору")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656553() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        UUID strategyOther = strategyService.findOneContract().get().getId();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyOther);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect strategy contract не равно", errorCode, is("0350-06-B17"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("656557")
    @DisplayName("C656557.CreateSignal.Стратегия strategyId не существует")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656557() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Strategy not found не равно", errorCode, is("0350-06-B06"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("657123")
    @DisplayName("C657123.CreateSignal.Не найдена запись в таблице master_potfolio")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657123() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        assertThat("код ошибки Master portfolio not found не равно", jsonObject.getString("errorCode"), is("0350-06-B22"));
        assertThat("Сообщение об ошибке не равно", jsonObject.getString("errorMessage"), is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("657138")
    @DisplayName("C657138.CreateSignal.Version из запроса != master_portfolio.version найденного портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657138() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 5;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, 3, "12.0", "3556.78");
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect master portfolio version не равно",errorCode, is("0350-06-B23"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Виртуальный портфель устарел"));
    }


    @SneakyThrows
    @Test
    @AllureId("657144")
    @DisplayName("C657144.CreateSignal.Позиция не найдена в кэше trackingExchangePositionCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657144() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 5;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker("GMKN1022");
        request.setTradingClearingAccount("L01+00000SPB");
        request.setVersion(version);
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
        assertThat("код ошибки Tracking exchange position not found не равно",errorCode, is("0350-06-B15"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("657162")
    @DisplayName("C657162.CreateSignal.Pасписание не найдено в кэше exchangeTradingScheduleCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657162() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 5;
        String ticker = "TEST";
        String tradingClearingAccount = "TEST";
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.FX, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Trading schedule not found version не равно",errorCode, is("0350-06-B18"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Торговая площадка не работает"));
    }

    @SneakyThrows
    @Test
    @AllureId("657198")
    @DisplayName("C657198.CreateSignal.Позиция не найдена в кэше exchangePositionCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657198() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 5;
        String ticker = "MTS_TEST";
        String tradingClearingAccount = "L01+00000SPB";
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Exchange position not found не равно",errorCode, is("0350-06-B20"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("656524")
    @DisplayName("C656524.CreateSignal.Биржа не работает")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C656524() {
        double price = 10.0;
        int quantityRequest = 4;
        int version = 5;
        String ticker = "XS0743596040";
        String tradingClearingAccount = "L01+00000F00";
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.MOEX, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Trading schedule doesn't work now не равно",errorCode, is("0350-06-B19"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("657204")
    @DisplayName("C657204.CreateSignal.Quantity из запроса / значение lot из exchangePositionCache != целое число")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657204() {
        double price = 10.0;
        int quantityRequest = 3;
        int version = 4;
        String ticker = "KO";
        String tradingClearingAccount = "L01+00000SPB";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //отправляем событие в fireg.instrument
        String event = KafkaModelFiregInstrumentCocaColaEvent.getKafkaTemplate(LocalDateTime.now(), "2");
        String key = "BBG000BMX289";
        //отправляем событие в топик kafka social.event
        stringSenderService.send(Topics.FIREG_INSTRUMENT, key, event);
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.MOEX, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Not integer number of lots to change не равно",errorCode, is("0350-06-B21"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Необходимо указать целое число лотов"));
    }

    @SneakyThrows
    @Test
    @AllureId("657314")
    @DisplayName("C657314.CreateSignal.Полученное значение quantity денежной позиции < 0, action = 'buy'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657314() {
        double price = 1000.0;
        int quantityRequest = 6;
        int version = 2;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.BUY);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Not enough money in portfolio to increase не равно",errorCode, is("0350-06-B24"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно денег в портфеле для увеличения позиции"));
    }

    @SneakyThrows
    @Test
    @AllureId("657490")
    @DisplayName("C657490.CreateSignal.Позиция не найдена в master_portfolio.position, action = 'sell'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657490() {
        double price = 10.0;
        int quantityRequest = 6;
        int version = 2;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio("CL", "L01+00000SPB", version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Not enough asset in the portfolio to decrease the position не равно",errorCode, is("0350-06-B25"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно актива в портфеле для уменьшения позиции"));
    }

    @SneakyThrows
    @Test
    @AllureId("657523")
    @DisplayName("C657523.CreateSignal.Quantity позиции из запроса > master_portfolio_position.quantity, action = 'sell'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657523() {
        double price = 10.0;
        int quantityRequest = 30;
        int version = 2;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Not enough asset in the portfolio to decrease the position не равно",errorCode, is("0350-06-B25"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно актива в портфеле для уменьшения позиции"));
    }


    @SneakyThrows
    @Test
    @AllureId("657577")
    @DisplayName("C657577.CreateSignal.Позиция не доступна для автоследования, tracking_allowed = false")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C657577() {
        double price = 10.0;
        int quantityRequest = 30;
        int version = 2;
        String ticker = "MTS0620";
        String tradingClearingAccount = "L01+00000SPB";
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, false, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.BUY);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Position is not available for tracking не равно",errorCode, is("0350-06-B26"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("658177")
    @DisplayName("C658177.CreateSignalНе совпадает валюта стратегии и позиции, значение currency из кэша != strategy.base_currency")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C658177() {
        double price = 10.0;
        int quantityRequest = 30;
        int version = 2;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, false, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.BUY);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect currency не равно",errorCode, is("0350-06-B28"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("658142")
    @DisplayName("C658142.CreateSignal.У позиции отсутствует один из необходимых параметров (type, currency) в exchangePositionCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C658142() {
        double price = 10.0;
        int quantityRequest = 3;
        int version = 4;
        String ticker = "KO";
        String tradingClearingAccount = "L01+00000SPB";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //отправляем событие в fireg.instrument
        String event = KafkaModelFiregInstrumentCoCaNoType.getKafkaTemplate(LocalDateTime.now());
        String key = "BBG000BMX289";
        //отправляем событие в топик kafka social.event
        stringSenderService.send(Topics.FIREG_INSTRUMENT, key, event);
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.MOEX, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect position for strategy не равно",errorCode, is("0350-06-B27"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("658142")
    @DisplayName("C658142.CreateSignal.У позиции отсутствует один из необходимых параметров (type, currency) в exchangePositionCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C658142_1() {
        double price = 10.0;
        int quantityRequest = 3;
        int version = 4;
        String ticker = "KO";
        String tradingClearingAccount = "L01+00000SPB";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //отправляем событие в fireg.instrument
        String event = KafkaModelFiregInstrumentCoCaNoCurr.getKafkaTemplate(LocalDateTime.now());
        String key = "BBG000BMX289";
        //отправляем событие в топик kafka social.event
        stringSenderService.send(Topics.FIREG_INSTRUMENT, key, event);
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.MOEX, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect position for strategy не равно",errorCode, is("0350-06-B27"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }




    private static Stream<Arguments> provideRiskLevelError() {
        return Stream.of(
            Arguments.of("0", StrategyRiskProfile.conservative),
            Arguments.of("0", StrategyRiskProfile.moderate),
            Arguments.of("1", StrategyRiskProfile.conservative)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRiskLevelError")
    @AllureId("658178")
    @DisplayName("C658178.CreateSignal.Риск-профиль позиции превышает риск-профиль стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C658178(String riskInst, StrategyRiskProfile strategyRiskProfile) {
        double price = 10.0;
        int quantityRequest = 3;
        int version = 4;
        String ticker = "W";
        String tradingClearingAccount = "TKCBM_TCAB";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //отправляем событие в fireg.instrument
        String event = KafkaModelFiregInstrumentWayfairWithRiskEvent.getKafkaTemplate(LocalDateTime.now(), riskInst);
        String key = "BBG001B17MV2";
        //отправляем событие в топик kafka social.event
        stringSenderService.send(Topics.FIREG_INSTRUMENT, key, event);
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, strategyRiskProfile, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.SELL);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
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
            .respSpec(spec -> spec.expectStatusCode(422));
        createSignal.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createSignal.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки Incorrect position for strategy не равно",errorCode, is("0350-06-B29"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Данный сигнал недоступен"));
    }

    /////////***методы для работы тестов**************************************************************************

    //метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
    UUID createClientWithStrategy(UUID investId, ClientStatusType сlientStatusType, String money,
                                  ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency baseCurrency,
                                  ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile riskProfile) {
        client = clientService.createClient(investId, сlientStatusType, null);
        //формируем тело запроса метода createStrategy
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(baseCurrency);
        request.setDescription("test strategy by autotest");
        request.setRiskProfile(riskProfile);
        request.setTitle("test strategy createSignal");
        request.setBaseMoneyPositionQuantity(new BigDecimal(money));
        // вызываем метод CreateStrategy
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
        UUID strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
        return strategyId;
    }


    //метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
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
                .reqSpec(r -> r.addHeader("api-key", "tracking"))
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

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(UUID investId, String contractId, ContractRole contractRole,
                                             ContractState contractState, UUID strategyId, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) throws JsonProcessingException {

        //создаем запись о клиенте в tracking.client
        client = clientService.createClient(investId, ClientStatusType.registered, null);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = contractService.saveContract(contract);
        //создаем запись о стратегии клиента
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle("test strategy by autotest")
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription("test strategy createSignal")
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategy = strategyService.saveStrategy(strategy);
    }

    void createMasterPortfolio(String ticker, String tradingClearingAccount, int version, String quantityPos, String money) {
        //создаем портфель master в cassandra
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .build());
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractId, strategyId, version, baseMoneyPosition, positionList);
    }

}
