package stpTrackingAdminApi.updateExchangePosition;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.core.IsNull;
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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteToByteReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionRequest;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ExchangePositionExchange;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;
import ru.tinkoff.trading.tracking.Tracking;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.EXCHANGE_POSITION;
@Slf4j
@Epic("UpdateExchangePosition - Редактирования биржевой позиции")
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
public class UpdateExchangePositionTest {
    ExchangePositionApi exchangePositionApi = ApiClient.api(ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;
    String xApiKey = "x-api-key";

    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    ByteToByteReceiverService kafkaReceiver;
    @AfterEach
    void deleteClient() {
        step("Удаляем инструмент автоследования", () -> {
            exchangePositionService.deleteExchangePosition(exchangePosition);
        });
    }

    private static Stream<Arguments> provideUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("EUR_RUB", "CETS")

        );
    }

    @ParameterizedTest
    @MethodSource("provideUpdateExchangePosition")
    @AllureId("531469")
    @DisplayName("C531469.UpdateExchangePosition.Успешное редактирование биржевой позиции")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531469(String otcTicker, String otcClassCode) throws Exception {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "MB9885503216";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        String otcTickerNew = "EUR_RUB123";
        String otcClassCodeNew = "CETS123";
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, otcTicker, otcClassCode);
        //вычитываем все события из tracking.exchange-position
        resetOffsetToLate(EXCHANGE_POSITION);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestAllParam(ticker, tradingClearingAccount,
            otcTickerNew, otcClassCodeNew, limit, period, dailyQuantityLimit, ExchangePosition.ExchangeEnum.SPB);
        //вызываем метод updateExchangePosition
        ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse expecResponse = exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse.class));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<byte[], byte[]>> messages = kafkaReceiver.receiveBatch(EXCHANGE_POSITION, Duration.ofSeconds(31));
        Pair<byte[], byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        //парсим сообщение
        Tracking.ExchangePositionId exchangePositionId = Tracking.ExchangePositionId.parseFrom(message.getKey());
        Tracking.ExchangePosition exchangePositionKafka = Tracking.ExchangePosition.parseFrom(message.getValue());
        //проверяем, что пришло в ответ от метода updateExchangePosition
        assertThat("ID инструмента не равен", expecResponse.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", expecResponse.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", expecResponse.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", expecResponse.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", expecResponse.getDailyQuantityLimit().intValue(), is(dailyQuantityLimit));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(0).getPeriodId(), is(period));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(0).getLimit(), is(limit));
        assertThat("Внебиржевой тикер инструмента не равен", expecResponse.getOtcTicker(), is(otcTickerNew));
        assertThat("Внебиржевой код класса инструмента не равен", expecResponse.getOtcClassCode(), is(otcClassCodeNew));
        //проверяем ключ сообщения топика kafka
        assertThat("ID инструмента не равен", exchangePositionId.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionId.getTradingClearingAccount(), is(tradingClearingAccount));
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", exchangePositionKafka.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionKafka.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", exchangePositionKafka.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePositionKafka.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePositionKafka.getDailyQuantityLimit().getValue(), is(dailyQuantityLimit));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(0).getPeriodId(), is(period));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(0).getLimit(), is(limit));
        assertThat("Внебиржевой тикер инструмента не равен", exchangePositionKafka.getOtcTicker().getValue(), is(otcTickerNew));
        assertThat("Внебиржевой код класса инструмента не равен", exchangePositionKafka.getOtcClassCode().getValue(), is(otcClassCodeNew));
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(dailyQuantityLimit));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get(period), is(limit));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(otcTickerNew));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(otcClassCodeNew));
    }


    @Test
    @AllureId("531730")
    @DisplayName("C531730.UpdateExchangePosition.Успешное редактирование биржевой позиции,параметры, которые не переданы в запросе выставляем значения = null")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531730() throws Exception {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "MB9885503216";
        String exchange = "SPB";
        String otcTicker = "EUR_RUB";
        String otcClassCode = "CETS";
        Integer limit = 100;
        String period = "additional_liquidity";
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, otcTicker, otcClassCode);
        //вычитываем все события из tracking.exchange-position
        resetOffsetToLate(EXCHANGE_POSITION);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true);
        ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse expecResponse = exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse.class));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<byte[], byte[]>> messages = kafkaReceiver.receiveBatch(EXCHANGE_POSITION, Duration.ofSeconds(31));
        Pair<byte[], byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        //парсим сообщение
        Tracking.ExchangePositionId exchangePositionId = Tracking.ExchangePositionId.parseFrom(message.getKey());
        Tracking.ExchangePosition exchangePositionKafka = Tracking.ExchangePosition.parseFrom(message.getValue());
        //проверяем, что пришло в ответ от метода updateExchangePosition
        assertThat("ID инструмента не равен", expecResponse.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", expecResponse.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", expecResponse.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", expecResponse.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", expecResponse.getDailyQuantityLimit(), is(IsNull.nullValue()));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(0).getPeriodId(), is(period));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(0).getLimit(), is(limit));
        assertThat("Внебиржевой тикер инструмента не равен", expecResponse.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Внебиржевой код класса инструмента не равен", expecResponse.getOtcClassCode(), is(IsNull.nullValue()));
       //проверяем ключ сообщения топика kafka
        assertThat("ID инструмента не равен", exchangePositionId.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionId.getTradingClearingAccount(), is(tradingClearingAccount));
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", exchangePositionKafka.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionKafka.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", exchangePositionKafka.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePositionKafka.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePositionKafka.getDailyQuantityLimit().getValue(), is(0));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(0).getPeriodId(), is(period));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(0).getLimit(), is(limit));
        assertThat("Внебиржевой тикер инструмента не равен", exchangePositionKafka.getOtcTicker().getValue(), is(""));
        assertThat("Внебиржевой код класса инструмента не равен", exchangePositionKafka.getOtcClassCode().getValue(), is(""));
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(IsNull.nullValue()));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get(period), is(limit));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    private static Stream<Arguments> provideStringsForHeadersUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersUpdateExchangePosition")
    @AllureId("531495")
    @DisplayName("C531495.UpdateExchangePosition.Валидация обязательных параметров в headers: x-app-name, x-tcs-login")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531495(String name, String login) {
        String ticker = "NVTK0221";
        String tradingClearingAccount = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePositionBody = createBodyRequestRequiredParamWithDayLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, dailyQuantityLimit, true);
        //вызываем метод updateExchangePosition
        ExchangePositionApi.UpdateExchangePositionOper updateExchangePosition = exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .body(updateExchangePositionBody)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            updateExchangePosition = updateExchangePosition.xAppNameHeader(name);
        }
        if (login != null) {
            updateExchangePosition = updateExchangePosition.xTcsLoginHeader(login);
        }
        updateExchangePosition.execute(ResponseBodyData::asString);
        //проверяем, что запись в tracking.exchange_position не изменилась
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    private static Stream<Arguments> provideStringsForBodyUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(null, "TKCBM_TCAB", ExchangePosition.ExchangeEnum.SPB, true, 1, "additional_liquidity"),
            Arguments.of("NVTK0221", null, ExchangePosition.ExchangeEnum.SPB, true, 1, "additional_liquidity"),
            Arguments.of("NVTK0221", "TKCBM_TCAB", null, true, 1, "additional_liquidity"),
            Arguments.of("NVTK0221", "TKCBM_TCAB", ExchangePosition.ExchangeEnum.SPB, null, 1, "additional_liquidity"),
            Arguments.of("NVTK0221", "TKCBM_TCAB", ExchangePosition.ExchangeEnum.SPB, true, null, "additional_liquidity"),
            Arguments.of("NVTK0221", "TKCBM_TCAB", ExchangePosition.ExchangeEnum.SPB, true, 1, null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForBodyUpdateExchangePosition")
    @AllureId("531496")
    @DisplayName("C531496.UpdateExchangePosition.Валидация обязательных параметров в body: ticker, tradingClearingAccount, exchange, trackingAllowed, orderQuantityLimits, periodId, limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531496(String ticker, String tradingClearingAccount, ExchangePosition.ExchangeEnum exchangeTest, Boolean trackingAllowed, Integer limit, String period) {
        String tickerOld = "NVTK0221";
        String tradingClearingAccountOld = "TKCBM_TCAB";
        String exchange = "SPB";
        //создаем запись в tracking.exchange_position
        createExchangePosition(tickerOld, tradingClearingAccountOld, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, exchangeTest, trackingAllowed);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(tickerOld, tradingClearingAccountOld);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }

    private static Stream<Arguments> provideTickerBodyUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("NVTK0221NVTK0")
        );
    }

    @ParameterizedTest
    @MethodSource("provideTickerBodyUpdateExchangePosition")
    @AllureId("531563")
    @DisplayName("C531563.UpdateExchangePosition.Валидация ticker: < 1 символа, > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531563(String ticker) {
        String tickerOld = "NVTK0221";
        String tradingClearingAccountOld = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        //создаем запись в tracking.exchange_position
        createExchangePosition(tickerOld, tradingClearingAccountOld, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParam(ticker, tradingClearingAccountOld,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(tickerOld, tradingClearingAccountOld);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    private static Stream<Arguments> provideTradingClearingAccountBodyUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("TKCBM_TCAB123")
        );
    }

    @ParameterizedTest
    @MethodSource("provideTradingClearingAccountBodyUpdateExchangePosition")
    @AllureId("531579")
    @DisplayName("C531579.UpdateExchangePosition.Валидация tradingClearingAccount: < 1 символа, > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531579(String tradingClearingAccount) {
        String tickerOld = "NVTK0221";
        String tradingClearingAccountOld = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        //создаем запись в tracking.exchange_position
        createExchangePosition(tickerOld, tradingClearingAccountOld, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParam(tickerOld, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, true);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(tickerOld, tradingClearingAccountOld);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    @Test
    @AllureId("531582")
    @DisplayName("C531582.UpdateExchangePosition.Валидация dailyQuantityLimit: значение < 1")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531582() {
        String ticker = "NVTK0221";
        String tradingClearingAccount = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 0;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParamWithDayLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, dailyQuantityLimit, true);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }

    @Test
    @AllureId("531583")
    @DisplayName("C531583.UpdateExchangePosition.Валидация orderQuantityLimits.periodId: значение < 1")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531583() {
        String ticker = "NVTK0221";
        String tradingClearingAccount = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
        //включаем kafkaConsumer и слушаем топик tracking.exchange-position
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParamWithDayLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, dailyQuantityLimit, true);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    private static Stream<Arguments> provideOtcTickerBodyUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("EUR_RUBEUR_RU")
        );
    }

    @ParameterizedTest
    @MethodSource("provideOtcTickerBodyUpdateExchangePosition")
    @AllureId("531590")
    @DisplayName("C531590.UpdateExchangePosition.Валидация otcTicker: < 1 символа, > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531590(String otcTicker) {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "MB9885503216";
        String exchange = "SPB";
        String otcTickerOld = "EUR_RUB";
        String otcClassCode = "CETS";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, otcTickerOld, otcClassCode);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestAllParam(ticker, tradingClearingAccount,
            otcTicker, otcClassCode, limit, period, dailyQuantityLimit, ExchangePosition.ExchangeEnum.SPB);
        //вызываем метод updateExchangePosition
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(otcTickerOld));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(otcClassCode));
    }


    private static Stream<Arguments> provideOtcClassCodeBodyUpdateExchangePosition() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("CETSCETSCETSC")
        );
    }

    @ParameterizedTest
    @MethodSource("provideOtcClassCodeBodyUpdateExchangePosition")
    @AllureId("531591")
    @DisplayName("C531591.UpdateExchangePosition.Валидация otcClassCode: < 1 символа, > 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531591(String otcClassCode) {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "MB9885503216";
        String exchange = "SPB";
        String otcTicker = "EUR_RUB";
        String otcClassCodeOld = "CETS";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, otcTicker, otcClassCodeOld);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestAllParam(ticker, tradingClearingAccount,
            otcTicker, otcClassCode, limit, period, dailyQuantityLimit, ExchangePosition.ExchangeEnum.SPB);
        //вызываем метод updateExchangePosition
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(otcTicker));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(otcClassCodeOld));
    }


    @Test
    @AllureId("531603")
    @DisplayName("C531603.UpdateExchangePosition.Авторизация: не передаем параметр api-key")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531603() {
        String ticker = "NVTK0221";
        String tradingClearingAccount = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParamWithDayLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, dailyQuantityLimit, true);
        exchangePositionApi.updateExchangePosition()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    @Test
    @AllureId("531604")
    @DisplayName("C531604.UpdateExchangePosition.Авторизация: передаем некорректное значение api-key")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531604() {
        String ticker = "NVTK0221";
        String tradingClearingAccount = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParamWithDayLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, dailyQuantityLimit, true);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "trading"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    @Test
    @AllureId("531608")
    @DisplayName("C531608.UpdateExchangePosition.В orderQuantityLimits существует более одного объекта с одинаковым periodId")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531608() {
        String ticker = "NVTK0221";
        String tradingClearingAccount = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
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
        UpdateExchangePositionRequest updateExchangePosition = new UpdateExchangePositionRequest();
        updateExchangePosition.exchange(ExchangePosition.ExchangeEnum.SPB);
        updateExchangePosition.setOrderQuantityLimits(orderQuantityLimitList);
        updateExchangePosition.setTicker(ticker);
        updateExchangePosition.setDailyQuantityLimit(dailyQuantityLimit);
        updateExchangePosition.setTrackingAllowed(true);
        updateExchangePosition.setTradingClearingAccount(tradingClearingAccount);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }

    private static Stream<Arguments> provideBodyUpdateExchangePosition() {
        return Stream.of(
            Arguments.of("NVTK0222", "TKCBM_TCAB"),
            Arguments.of("NVTK0221", "TKCBM_TCAV")
        );
    }

    @ParameterizedTest
    @MethodSource("provideBodyUpdateExchangePosition")
    @AllureId("531612")
    @DisplayName("C531612.UpdateExchangePosition.Обновление не существующей записи: ticker, tradingClearingAccount")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для редактирования биржевой позиции: включения/исключения позиции из списка разрешенных, редактирования атрибутов позиции.")
    void C531612(String ticker, String tradingClearingAccount) {
        String tickerOld = "NVTK0221";
        String tradingClearingAccountOld = "TKCBM_TCAB";
        String exchange = "SPB";
        Integer limit = 100;
        String period = "additional_liquidity";
        Integer dailyQuantityLimit = 100;
        //создаем запись в tracking.exchange_position
        createExchangePosition(tickerOld, tradingClearingAccountOld, ExchangePositionExchange.SPB, null, null);
        //формируем тело запроса
        UpdateExchangePositionRequest updateExchangePosition = createBodyRequestRequiredParamWithDayLimit(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.SPB, dailyQuantityLimit, true);
        exchangePositionApi.updateExchangePosition()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(tickerOld, tradingClearingAccountOld);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(false));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(100));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }


    //методы для работы тестов***********************************************************************
    //создаем запись в tracking.exchange_position по инструменту
    public void createExchangePosition(String ticker, String tradingClearingAccount, ExchangePositionExchange exchangePositionExchange,
                                       String otcTicker, String otcClassCode) {
        Map<String, Integer> mapValue = new HashMap<String, Integer>();
        mapValue.put("default", 100);
        mapValue.put("primary", 100);
        exchangePosition = new ru.qa.tinkoff.tracking.entities.ExchangePosition()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setExchangePositionExchange(exchangePositionExchange)
            .setTrackingAllowed(false)
            .setDailyQuantityLimit(200)
            .setOrderQuantityLimits(mapValue)
            .setOtcTicker(otcTicker)
            .setOtcClassCode(otcClassCode);
        exchangePosition = exchangePositionService.saveExchangePosition(exchangePosition);
    }


    //body запроса метода updateExchangePosition все парамерты
    public UpdateExchangePositionRequest createBodyRequestAllParam(String ticker, String tradingClearingAccount, String otcTicker,
                                                                   String otcClassCode, Integer limit, String period, Integer dailyQuantityLimit,
                                                                   ExchangePosition.ExchangeEnum exchange) {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        UpdateExchangePositionRequest updateExchangePosition = new UpdateExchangePositionRequest();
        updateExchangePosition.exchange(exchange);
        updateExchangePosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        updateExchangePosition.setTicker(ticker);
        updateExchangePosition.setDailyQuantityLimit(dailyQuantityLimit);
        updateExchangePosition.setTrackingAllowed(true);
        updateExchangePosition.setTradingClearingAccount(tradingClearingAccount);
        updateExchangePosition.setOtcTicker(otcTicker);
        updateExchangePosition.setOtcClassCode(otcClassCode);
        return updateExchangePosition;
    }

    //body запроса метода updateExchangePosition обязательные парамерты
    public UpdateExchangePositionRequest createBodyRequestRequiredParam(String ticker, String tradingClearingAccount, Integer limit, String period,
                                                                        ExchangePosition.ExchangeEnum exchange, Boolean trackingAllowed) {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        UpdateExchangePositionRequest updateExchangePosition = new UpdateExchangePositionRequest();
        updateExchangePosition.exchange(exchange);
        updateExchangePosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        updateExchangePosition.setTicker(ticker);
        updateExchangePosition.setTrackingAllowed(trackingAllowed);
        updateExchangePosition.setTradingClearingAccount(tradingClearingAccount);
        return updateExchangePosition;
    }

    //body запроса метода updateExchangePosition с параметром DailyQuantityLimit
    public UpdateExchangePositionRequest createBodyRequestRequiredParamWithDayLimit(String ticker, String tradingClearingAccount, Integer limit, String period,
                                                                                    ExchangePosition.ExchangeEnum exchange, Integer dailyQuantityLimit, Boolean trackingAllowed) {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        UpdateExchangePositionRequest updateExchangePosition = new UpdateExchangePositionRequest();
        updateExchangePosition.exchange(exchange);
        updateExchangePosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        updateExchangePosition.setTicker(ticker);
        updateExchangePosition.setDailyQuantityLimit(dailyQuantityLimit);
        updateExchangePosition.setTrackingAllowed(true);
        updateExchangePosition.setTradingClearingAccount(tradingClearingAccount);
        return updateExchangePosition;
    }


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }
}
