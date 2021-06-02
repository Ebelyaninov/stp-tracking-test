package stpTrackingAdminApi.createExchangePosition;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;
@Slf4j
@Epic("CreateExchangePosition - Добавление биржевой позиции")
@Feature("TAP-7084")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class

})
public class CreateExchangePositionSuccessTest {
    ExchangePositionApi exchangePositionApi = ApiClient.api(ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;
    @Autowired
    ByteToByteReceiverService kafkaReceiver;
    @Autowired
    ExchangePositionService exchangePositionService;

    @AfterEach
    void deleteClient() {
        step("Удаляем инструмент автоследования", () -> {
            exchangePositionService.deleteExchangePosition(exchangePosition);
        });
    }

    @Test
    @AllureId("521352")
    @DisplayName("C521352.CreateExchangePosition.Добавление биржевой позиции: обязательные параметры")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521352() throws Exception {
        String ticker = "FXGD";
        String tradingClearingAccount = "L01+00002F00";
        String exchange = "MOEX";
        Integer limit = 100;
        String period = "additional_liquidity";
        //вычитываем все события из tracking.exchange-position
        resetOffsetToLate(EXCHANGE_POSITION);
            //формируем тело запроса
            var сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
                limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
            //вызываем метод createExchangePosition
            var expecResponse = exchangePositionApi.createExchangePosition()
                .reqSpec(r -> r.addHeader("api-key", "tracking"))
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("android")
                .xDeviceIdHeader("test")
                .xTcsLoginHeader("tracking_admin")
                .body(сreateExchangePositionRequest)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse.class));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<byte[], byte[]>> messages = kafkaReceiver.receiveBatch(EXCHANGE_POSITION, Duration.ofSeconds(31));
        Pair<byte[], byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
            //проверяем, что пришло в ответ от метода createExchangePosition
            assertThat("ID инструмента не равен", expecResponse.getTicker(), is(ticker));
            assertThat("Торгово-клиринговый счет не равен", expecResponse.getTradingClearingAccount(), is(tradingClearingAccount));
            assertThat("Код биржи не равен", expecResponse.getExchange().toString(), is(exchange));
            assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", expecResponse.getTrackingAllowed(), is(true));
            assertThat("Лимит количества единиц по сессии не равен", expecResponse.getDailyQuantityLimit(), is(IsNull.nullValue()));
            assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(0).getPeriodId(), is(period));
            assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(0).getLimit(), is(limit));
            assertThat("Внебиржевой тикер инструмента не равен", expecResponse.getOtcTicker(), is(IsNull.nullValue()));
            assertThat("Внебиржевой код класса инструмента не равен", expecResponse.getOtcClassCode(), is(IsNull.nullValue()));
            //парсим сообщение
            Tracking.ExchangePositionId exchangePositionId = Tracking.ExchangePositionId.parseFrom(message.getKey());
            Tracking.ExchangePosition exchangePositionKafka = Tracking.ExchangePosition.parseFrom(message.getValue());
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


    @Test
    @AllureId("521350")
    @DisplayName("C521350.CreateExchangePosition.Добавление биржевой позиции с несколькими orderQuantityLimits")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521350() throws Exception {
        String ticker = "FXGD";
        String tradingClearingAccount = "L01+00002F00";
        String exchange = "MOEX";
        Integer dailyQuantityLimit = 1000;
        //вычитываем все события из tracking.exchange-position
        resetOffsetToLate(EXCHANGE_POSITION);
        //формируем тело запроса
        List<OrderQuantityLimit> orderQuantityLimitList = new ArrayList<>();
        orderQuantityLimitList.add(new OrderQuantityLimit().limit(500).periodId("default"));
        orderQuantityLimitList.add(new OrderQuantityLimit().limit(200).periodId("main_trading"));
        orderQuantityLimitList.add(new OrderQuantityLimit().limit(200).periodId("additional_liquidity"));
        orderQuantityLimitList.add(new OrderQuantityLimit().limit(100).periodId("primary"));
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamQuantityLimitList(ticker,
            tradingClearingAccount, orderQuantityLimitList, ExchangePosition.ExchangeEnum.MOEX, true, dailyQuantityLimit);
        //вызываем метод createExchangePosition
        ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse expecResponse = exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
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
        //проверяем, что пришло в ответ от метода createExchangePosition
        checkResponseFromCreateExchangePosition(ticker, tradingClearingAccount, exchange, dailyQuantityLimit, expecResponse);
        //проверяем ключ сообщения топика kafka
        assertThat("ID инструмента не равен", exchangePositionId.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionId.getTradingClearingAccount(), is(tradingClearingAccount));
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", exchangePositionKafka.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionKafka.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", exchangePositionKafka.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePositionKafka.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePositionKafka.getDailyQuantityLimit().getValue(), is(dailyQuantityLimit));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(0).getPeriodId(), is("default"));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(0).getLimit(), is(500));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(1).getPeriodId(), is("main_trading"));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(1).getLimit(), is(200));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(2).getPeriodId(), is("additional_liquidity"));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(2).getLimit(), is(200));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(3).getPeriodId(), is("primary"));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(3).getLimit(), is(100));
        assertThat("Внебиржевой тикер инструмента не равен", exchangePositionKafka.getOtcTicker().getValue(), is(""));
        assertThat("Внебиржевой код класса инструмента не равен", exchangePositionKafka.getOtcClassCode().getValue(), is(""));
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(dailyQuantityLimit));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(500));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("main_trading"), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("additional_liquidity"), is(200));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("primary"), is(100));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
    }



    @Test
    @AllureId("521346")
    @DisplayName("C521346.CreateExchangePosition.Добавление биржевой позиции все параметры")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521346() throws Exception {
        String ticker = "EUR_RUB__TOM";
        String tradingClearingAccount = "MB9885503216";
        String exchange = "MOEX";
        Integer dailyQuantityLimit = 1000;
        String otcTicker = "EUR_RUB";
        String otcClassCode = "CETS";
        //вычитываем все события из tracking.exchange-position
        resetOffsetToLate(EXCHANGE_POSITION);
        //формируем тело запроса
        List<OrderQuantityLimit> orderQuantityLimitList
            = new ArrayList<>();
        OrderQuantityLimit orderQuantityLimit = new OrderQuantityLimit();
        orderQuantityLimit.setLimit(500);
        orderQuantityLimit.setPeriodId("default");
        orderQuantityLimitList.add(orderQuantityLimit);
        orderQuantityLimit = new OrderQuantityLimit();
        orderQuantityLimit.setLimit(500);
        orderQuantityLimit.setPeriodId("main_trading");
        orderQuantityLimitList.add(orderQuantityLimit);
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestParamOct(ticker,
            tradingClearingAccount, orderQuantityLimitList, ExchangePosition.ExchangeEnum.MOEX, true, dailyQuantityLimit, otcTicker, otcClassCode);
        //вызываем метод createExchangePosition
        ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse expecResponse = exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
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
        //проверяем, что пришло в ответ от метода createExchangePosition
        assertThat("ID инструмента не равен", expecResponse.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", expecResponse.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", expecResponse.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", expecResponse.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", expecResponse.getDailyQuantityLimit(), is(dailyQuantityLimit));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(0).getPeriodId(), is("default"));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(0).getLimit(), is(500));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(1).getPeriodId(), is("main_trading"));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(1).getLimit(), is(500));
        assertThat("Внебиржевой тикер инструмента не равен", expecResponse.getOtcTicker(), is(otcTicker));
        assertThat("Внебиржевой код класса инструмента не равен", expecResponse.getOtcClassCode(), is(otcClassCode));
        //проверяем ключ сообщения топика kafka
        assertThat("ID инструмента не равен", exchangePositionId.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionId.getTradingClearingAccount(), is(tradingClearingAccount));
        //проверяем message топика kafka
        assertThat("ID инструмента не равен", exchangePositionKafka.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", exchangePositionKafka.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", exchangePositionKafka.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePositionKafka.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePositionKafka.getDailyQuantityLimit().getValue(), is(dailyQuantityLimit));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(0).getPeriodId(), is("default"));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(0).getLimit(), is(500));
        assertThat("Идентификатор периода не равен", exchangePositionKafka.getOrderQuantityLimit(1).getPeriodId(), is("main_trading"));
        assertThat("Лимит количества единиц актива по заявке не равен", exchangePositionKafka.getOrderQuantityLimit(1).getLimit(), is(500));
        assertThat("Внебиржевой тикер инструмента не равен", exchangePositionKafka.getOtcTicker().getValue(), is(otcTicker));
        assertThat("Внебиржевой код класса инструмента не равен", exchangePositionKafka.getOtcClassCode().getValue(), is(otcClassCode));
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(dailyQuantityLimit));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("default"), is(500));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get("main_trading"), is(500));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(otcTicker));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(otcClassCode));
    }


    @Test
    @AllureId("521302")
    @DisplayName("C521302.CreateExchangePosition.Инструмент уже существует")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для добавления разрешенной биржевой позиции для автоследования.")
    void C521302() {
        String ticker = "FXGD";
        String tradingClearingAccount = "L01+00002F00";
        String exchange = "MOEX";
        Integer limit = 100;
        String period = "additional_liquidity";
        //формируем тело запроса
        CreateExchangePositionRequest сreateExchangePositionRequest = createBodyRequestRequiredParam(ticker, tradingClearingAccount,
            limit, period, ExchangePosition.ExchangeEnum.MOEX, true);
        //вызываем метод createExchangePosition
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //проверяем запись в tracking.exchange_position
        exchangePosition = exchangePositionService.getExchangePositionByTicker(ticker, tradingClearingAccount);
        assertThat("Код биржи не равен", exchangePosition.getExchangePositionExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", exchangePosition.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", exchangePosition.getDailyQuantityLimit(), is(IsNull.nullValue()));
        assertThat("Лимит и период количества единиц актива по заявке не равен",
            exchangePosition.getOrderQuantityLimits().get(period), is(limit));
        assertThat("Тикер внебиржевого инструмента не равен", exchangePosition.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Код класса внебиржевого инструмента не равен", exchangePosition.getOtcClassCode(), is(IsNull.nullValue()));
        //вызываем метод повторно
        exchangePositionApi.createExchangePosition()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(сreateExchangePositionRequest)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);

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

    //body запроса метода updateExchangePosition обязательные парамерты orderQuantityLimitList передаем отдельно списком
    public CreateExchangePositionRequest createBodyRequestParamQuantityLimitList(String ticker, String tradingClearingAccount, List<OrderQuantityLimit> orderQuantityLimitList,
                                                                                 ExchangePosition.ExchangeEnum exchange, Boolean trackingAllowed, Integer dailyQuantityLimit) {
        CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
        createExPosition.exchange(exchange);
        createExPosition.dailyQuantityLimit(dailyQuantityLimit);
        createExPosition.setOrderQuantityLimits(orderQuantityLimitList);
        createExPosition.setTicker(ticker);
        createExPosition.setTrackingAllowed(trackingAllowed);
        createExPosition.setTradingClearingAccount(tradingClearingAccount);
        return createExPosition;
    }


    //body запроса метода updateExchangePosition обязательные парамерты orderQuantityLimitList передаем отдельно списком
    public CreateExchangePositionRequest createBodyRequestParamOct(String ticker, String tradingClearingAccount, List<OrderQuantityLimit> orderQuantityLimitList,
                                                                   ExchangePosition.ExchangeEnum exchange, Boolean trackingAllowed,
                                                                   Integer dailyQuantityLimit, String otcTicker, String otcClassCode) {
        CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
        createExPosition.exchange(exchange);
        createExPosition.dailyQuantityLimit(dailyQuantityLimit);
        createExPosition.setOrderQuantityLimits(orderQuantityLimitList);
        createExPosition.setTicker(ticker);
        createExPosition.setTrackingAllowed(trackingAllowed);
        createExPosition.setTradingClearingAccount(tradingClearingAccount);
        createExPosition.setOtcTicker(otcTicker);
        createExPosition.setOtcClassCode(otcClassCode);
        return createExPosition;
    }

    private void checkResponseFromCreateExchangePosition(String ticker, String tradingClearingAccount, String exchange, Integer dailyQuantityLimit, ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse expecResponse) {
        assertThat("ID инструмента не равен", expecResponse.getTicker(), is(ticker));
        assertThat("Торгово-клиринговый счет не равен", expecResponse.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Код биржи не равен", expecResponse.getExchange().toString(), is(exchange));
        assertThat("Признак разрешённой для торговли в автоследовании позиции не равен", expecResponse.getTrackingAllowed(), is(true));
        assertThat("Лимит количества единиц по сессии не равен", expecResponse.getDailyQuantityLimit(), is(dailyQuantityLimit));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(0).getPeriodId(), is("default"));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(0).getLimit(), is(500));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(1).getPeriodId(), is("main_trading"));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(1).getLimit(), is(200));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(2).getPeriodId(), is("additional_liquidity"));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(2).getLimit(), is(200));
        assertThat("Идентификатор периода не равен", expecResponse.getOrderQuantityLimits().get(3).getPeriodId(), is("primary"));
        assertThat("Лимит количества единиц актива по заявке не равен", expecResponse.getOrderQuantityLimits().get(3).getLimit(), is(100));
        assertThat("Внебиржевой тикер инструмента не равен", expecResponse.getOtcTicker(), is(IsNull.nullValue()));
        assertThat("Внебиржевой код класса инструмента не равен", expecResponse.getOtcClassCode(), is(IsNull.nullValue()));
    }


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }
}
