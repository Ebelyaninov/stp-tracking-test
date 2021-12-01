package stpTrackingAdminApi.createExchangePosition;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("CreateExchangePosition - Добавление биржевой позиции")
@Feature("TAP-7084")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class
})
public class CreateExchangePositionErrorTest {
    ExchangePositionApi exchangePositionApi = ApiClient.api(ApiClient.Config.apiConfig()).exchangePosition();

    @Autowired
    ExchangePositionService exchangePositionService;

    private static Stream<Arguments> provideStringsForHeadersCreateExchangePosition() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }
    String xApiKey = "x-api-key";
    String ticker = "FXGD";
    String tradingClearingAccount = "NDS000000001";



    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCreateExchangePosition")
    @AllureId("520697")
    @DisplayName("C520697.CreateExchangePosition.Валидация запроса: обязательные параметры в headers :x-app-name, x-tcs-login")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C520697(String name, String login)  {
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
            //вызываем метод createExchangePosition
            ExchangePositionApi.CreateExchangePositionOper createExchangePosition = exchangePositionApi.createExchangePosition()
                .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
                .body(сreateExchangePositionRequest)
                .respSpec(spec -> spec.expectStatusCode(400));
            if (name != null) {
                createExchangePosition = createExchangePosition.xAppNameHeader(name);
            }
            if (login != null) {
                createExchangePosition = createExchangePosition.xTcsLoginHeader(login);
            }
            createExchangePosition.execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    private static Stream<Arguments> provideStringsForBodyCreateExchangePosition() {
        return Stream.of(
            Arguments.of(null, "NDS000000001", ExchangePosition.ExchangeEnum.MOEX, true, 1, "additional_liquidity"),
            Arguments.of("FXGD", null, ExchangePosition.ExchangeEnum.MOEX, true, 1, "additional_liquidity"),
            Arguments.of("FXGD", "NDS000000001", null, true, 1, "additional_liquidity"),
            Arguments.of("FXGD", "NDS000000001", ExchangePosition.ExchangeEnum.MOEX, null, 1, "additional_liquidity"),
            Arguments.of("FXGD", "NDS000000001", ExchangePosition.ExchangeEnum.MOEX, true, null, "additional_liquidity"),
            Arguments.of("FXGD", "NDS000000001", ExchangePosition.ExchangeEnum.MOEX, true, 1, null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForBodyCreateExchangePosition")
    @AllureId("520826")
    @DisplayName("C520826.CreateExchangePosition.Валидация запроса: обязательные параметры в body: ticker, tradingClearingAccount, exchange, trackingAllowed, orderQuantityLimits, periodId, limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C520826(String ticker, String tradingClearingAccount, ExchangePosition.ExchangeEnum exchangeTest, Boolean trackingAllowed, Integer limit, String period)  {
                //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, exchangeTest, trackingAllowed);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker("FXGD", "L01+00002F00");
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("520851")
    @DisplayName("C520851.CreateExchangePosition.Валидация запроса: ticker < 1 символа")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C520851()  {
        String ticker = "";
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
            //вызываем метод createExchangePosition
            exchangePositionApi.createExchangePosition()
                .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("android")
                .xDeviceIdHeader("test")
                .xTcsLoginHeader("tracking_admin")
                .body(сreateExchangePositionRequest)
                .respSpec(spec -> spec.expectStatusCode(400))
                .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("520861")
    @DisplayName("C520861.CreateExchangePosition.Валидация запроса: ticker > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C520861()  {
        String ticker = "FXGDFXGDFXGDF";
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
       //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("520878")
    @DisplayName("C520878.CreateExchangePosition.Валидация запроса: tradingClearingAccount < 1 символа")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C520878()  {
        String tradingClearingAccount = "";
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("520885")
    @DisplayName("C520885.CreateExchangePosition.Валидация запроса: tradingClearingAccount > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C520885()  {
        String tradingClearingAccount = "L01+00002F001";
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521188")
    @DisplayName("C521188.CreateExchangePosition.Валидация запроса: dailyQuantityLimit < 1 значения")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521188()  {
        String tradingClearingAccount = "L01+00002F001";
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamDailyQuantityLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true, 0);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521208")
    @DisplayName("C521208.CreateExchangePosition.Валидация запроса: periodId < 1 значения")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521208() throws Exception {
        String tradingClearingAccount = "L01+00002F001";
        Integer limit = 100;
        String period = "";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamDailyQuantityLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true, 100);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521225")
    @DisplayName("C521225.CreateExchangePosition.Валидация запроса: otcTicker < 1 символа")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521225() {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "NDS000000001";
        Integer limit = 100;
        String period = "additional_liquidity";
        String otcTicker = "";
        String otcClassCode = "CETS";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamOct(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true, 100, otcTicker, otcClassCode);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521238")
    @DisplayName("C521238.CreateExchangePosition.Валидация запроса: otcTicker > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521238() {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "NDS000000001";
        Integer limit = 100;
        String period = "additional_liquidity";
        String otcTicker = "CETSCETSCETSS";
        String otcClassCode = "CETS";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamOct(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true, 100, otcTicker, otcClassCode);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521243")
    @DisplayName("C521243.CreateExchangePosition.Валидация запроса: otcClassCode < 1 символа")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521243() {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "NDS000000001";
        Integer limit = 100;
        String period = "additional_liquidity";
        String otcTicker = "EUR_RUB";
        String otcClassCode = "";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamOct(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true, 100, otcTicker, otcClassCode);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521250")
    @DisplayName("C521250.CreateExchangePosition.Валидация запроса: otcClassCode > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521250() {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "NDS000000001";
        Integer limit = 100;
        String period = "additional_liquidity";
        String otcTicker = "EUR_RUB";
        String otcClassCode = "CETSCETSCETSS";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamOct(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true, 100, otcTicker, otcClassCode);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521257")
    @DisplayName("C521257.CreateExchangePosition.Авторизация: не передаем параметр apiKey")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521257()  {
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamDailyQuantityLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true, 100);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521266")
    @DisplayName("C521266.CreateExchangePosition.Авторизация: передаем неверный параметр apiKey")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521266()  {
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamDailyQuantityLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true, 100);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "trading"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("521288")
    @DisplayName("C521288.CreateExchangePosition.В orderQuantityLimits > 1 объекта с одинаковым periodId")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521288() throws Exception {
        //формируем тело запроса
        List<OrderQuantityLimit> orderQuantityLimitList
            = new ArrayList<>();
        OrderQuantityLimit orderQuantityLimit = new OrderQuantityLimit();
        orderQuantityLimit.setLimit(500);
        orderQuantityLimit.setPeriodId("default");
        orderQuantityLimitList.add(orderQuantityLimit);
        orderQuantityLimit = new OrderQuantityLimit();
        orderQuantityLimit.setLimit(500);
        orderQuantityLimit.setPeriodId("default");
        orderQuantityLimitList.add(orderQuantityLimit);
        CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
        createExPosition.exchange(ExchangePosition.ExchangeEnum.MOEX);
        createExPosition.setOrderQuantityLimits(orderQuantityLimitList);
        createExPosition.setTicker(ticker);
        createExPosition.setTrackingAllowed(true);
        createExPosition.setTradingClearingAccount(tradingClearingAccount);
        createExPosition.setDailyQuantityLimit(1000);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(createExPosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("запись по инструменту не равно",  exchangePositionOpt.isPresent(), is(false));
    }

    //методы для работы тестов:********************************************************************

    //body запроса метода updateExchangePosition обязательные парамерты
    public CreateExchangePositionRequest createBodyRequestRequiredParam(String ticker, String tradingClearingAccount, Integer limit, String period,
                                                                        ExchangePosition.ExchangeEnum exchange, Boolean trackingAllowed) {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
        createExPosition.exchange(exchange);
        createExPosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        createExPosition.setTicker(ticker);
        createExPosition.setTrackingAllowed(trackingAllowed);
        createExPosition.setTradingClearingAccount(tradingClearingAccount);
        return createExPosition;
    }


    //body запроса метода updateExchangePosition парамерт DailyQuantityLimit
    public CreateExchangePositionRequest createBodyRequestParamDailyQuantityLimit(String ticker, String tradingClearingAccount, Integer limit, String period,
                                                                        ExchangePosition.ExchangeEnum exchange, Boolean trackingAllowed,
                                                                        Integer DailyQuantityLimit ) {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
        createExPosition.exchange(exchange);
        createExPosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        createExPosition.setTicker(ticker);
        createExPosition.setTrackingAllowed(trackingAllowed);
        createExPosition.setTradingClearingAccount(tradingClearingAccount);
        createExPosition.setDailyQuantityLimit(DailyQuantityLimit);
        return createExPosition;
    }

    //body запроса метода updateExchangePosition парамерты для  внебиржевого инструмента
    public CreateExchangePositionRequest createBodyRequestParamOct(String ticker, String tradingClearingAccount, Integer limit, String period,
                                                                                  ExchangePosition.ExchangeEnum exchange, Boolean trackingAllowed,
                                                                                  Integer DailyQuantityLimit, String otcTicker, String otcClassCode) {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
        createExPosition.exchange(exchange);
        createExPosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        createExPosition.setTicker(ticker);
        createExPosition.setTrackingAllowed(trackingAllowed);
        createExPosition.setTradingClearingAccount(tradingClearingAccount);
        createExPosition.setDailyQuantityLimit(DailyQuantityLimit);
        createExPosition.setOtcTicker(otcTicker);
        createExPosition.setOtcClassCode(otcClassCode);
        return createExPosition;
    }








}
