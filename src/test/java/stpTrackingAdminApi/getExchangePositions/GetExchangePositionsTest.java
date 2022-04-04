package stpTrackingAdminApi.getExchangePositions;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ExchangePositionApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.Exchange;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetExchangePositionsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.ExchangePosition;
import ru.qa.tinkoff.tracking.entities.enums.ExchangePositionExchange;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;


@Slf4j
@Epic("getExchangePositions -  Получение биржевых позиций, добавленных в автоследование")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("getExchangePositions")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})
public class GetExchangePositionsTest {
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    ExchangePositionApiAdminCreator exchangePositionApiAdminCreator;
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;
    @Autowired
    ByteToByteReceiverService kafkaReceiver;
    @Autowired
    StpInstrument instrument;


    @AfterEach
    void deleteClient() {
        step("Удаляем инструмент автоследования", () -> {
            try {
                exchangePositionService.deleteExchangePosition(exchangePosition);
            } catch (Exception e) {
            }

        });
    }

    private static Stream<Arguments> provideStringsForHeadersGetExchangePosition() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersGetExchangePosition")
    @AllureId("1041093")
    @DisplayName("C1041093.GetExchangePositions.Валидация запроса: обязательные параметры в headers: x-app-name, x-tcs-login")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод необходим для получения списка всех позиций, участвующих в автоследовании.")
    void C1041093(String name, String login) {
        ExchangePositionApi.GetExchangePositionsOper getExchangePositions = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getExchangePositions = getExchangePositions.xAppNameHeader(name);
        }
        if (login != null) {
            getExchangePositions = getExchangePositions.xTcsLoginHeader(login);
        }
        getExchangePositions.execute(ResponseBodyData::asString);
    }


    @Test
    @AllureId("1045362")
    @DisplayName("C1045362.GetExchangePositions.Авторизация: не передаем X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1045362() {
        //получаем данные по клиенту  в api сервиса счетов
        exchangePositionApiAdminCreator.get().getExchangePositions()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("1045395")
    @DisplayName("C1045395.GetExchangePositions.Авторизация: Неверное значение X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1045395() {
        exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, "trading"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("1705727")
    @DisplayName("C1705727.GetExchangePositions.Авторизация: Неверное значение X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1705727() {
        exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    private static Stream<Arguments> provideLimit() {
        return Stream.of(
            Arguments.of(1),
            Arguments.of(2),
            Arguments.of(5),
            Arguments.of(12)

        );
    }

    @ParameterizedTest
    @MethodSource("provideLimit")
    @AllureId("1045326")
    @DisplayName("C1045326.GetExchangePositions.Получение биржевых позиций, добавленных в автоследование,передан параметр limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1045326(Integer limit) {
        //вызываем метод getExchangePositions
        GetExchangePositionsResponse responseExep = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .limitQuery(limit)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetExchangePositionsResponse.class));
        //проверяем, данные в сообщении
        assertThat("Количество возвращаемых записей не равно", responseExep.getItems().size(), is(limit));
    }


    @Test
    @AllureId("1047487")
    @DisplayName("1047487.GetExchangePositions.Получение биржевых позиций, добавленных в автоследование, передан параметр Cursor")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1047487() {
        //создаем инструмент в tracking.exchange_position
        steps.createExchangePosition("MHK", "L01+00000SPB", ExchangePositionExchange.SPB, null, null);
        exchangePosition = exchangePositionService.getExchangePositionByTicker("MHK", "L01+00000SPB");
        //получаем позицию
        Integer position = exchangePosition.getPosition();
        // находим список биржевых позиций по условию если передан курсор
        List<ExchangePosition> exchangePosition = exchangePositionService.getExchangePositionByPositionAndLimitmit(position, 1);
        //вызываем метод GetExchangePositions
        GetExchangePositionsResponse responseExep = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .cursorQuery(position)
            .limitQuery(1)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetExchangePositionsResponse.class));
//        //проверяем, данные в ответе метода и найденную биржевую позицию по условию из БД
        assertThat("ticker не равно", exchangePosition.get(0).getTicker(), is(responseExep.getItems().get(0).getTicker()));
        assertThat("tradingClearingAccount не равно", exchangePosition.get(0).getTradingClearingAccount(),
            is(responseExep.getItems().get(0).getTradingClearingAccount()));
        assertThat("exchange не равно", exchangePosition.get(0).getExchangePositionExchange().toString(),
            is(responseExep.getItems().get(0).getExchange().toString()));
        assertThat("trackingAllowed не равно", exchangePosition.get(0).getTrackingAllowed(),
            is(responseExep.getItems().get(0).getTrackingAllowed()));
        assertThat("dailyQuantityLimit не равно", exchangePosition.get(0).getDailyQuantityLimit(),
            is(responseExep.getItems().get(0).getDailyQuantityLimit()));
        assertThat("orderQuantityLimits не равно", exchangePosition.get(0).getOrderQuantityLimits().get("additional_liquidity"),
            is(responseExep.getItems().get(0).getOrderQuantityLimits().get(0).getLimit()));
        assertThat("otcTicker не равно", exchangePosition.get(0).getOtcTicker(),
            is(responseExep.getItems().get(0).getOtcTicker()));
        assertThat("otcClassCode не равно", exchangePosition.get(0).getOtcClassCode(),
            is(responseExep.getItems().get(0).getOtcClassCode()));
    }


    @Test
    @AllureId("1048275")
    @DisplayName("1048275.GetExchangePositions.Получение биржевых позиций, добавленных в автоследование, проверка nextCursor и hasNext")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1048275() {
        List<ExchangePosition> exchangePositions = exchangePositionService.getExchangePositionOrderByTickerAndTraAndTradingClearingAccount();
        //вызываем метод GetExchangePositions
        GetExchangePositionsResponse responseExep = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .cursorQuery(exchangePositions.get(exchangePositions.size() - 3).getPosition())
            .limitQuery(1)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetExchangePositionsResponse.class));
//        //проверяем, данные в ответе метода и найденную биржевую позицию по условию из БД
        assertThat("NextCursor не равно", exchangePositions.get(exchangePositions.size() - 2).getPosition().toString(),
            is(responseExep.getNextCursor().toString()));
        assertThat("hasNext не равно", true, is(responseExep.getHasNext()));
        //вызываем метод GetExchangePositions
        GetExchangePositionsResponse responseEx = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .cursorQuery(exchangePositions.get(exchangePositions.size() - 2).getPosition())
            .limitQuery(1)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetExchangePositionsResponse.class));
        assertThat("NextCursor не равно", exchangePositions.get(exchangePositions.size() - 1).getPosition().toString(),
            is(responseEx.getNextCursor().toString()));
        assertThat("hasNext не равно", false, is(responseEx.getHasNext()));

    }


    @Test
    @AllureId("1048468")
    @DisplayName("1048468.GetExchangePositions.Получение биржевых позиций, добавленных в автоследование, nextCursor is null")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1048468() {
        List<ExchangePosition> exchangePositions = exchangePositionService.getExchangePositionOrderByTickerAndTraAndTradingClearingAccount();
        //вызываем метод GetExchangePositions
        GetExchangePositionsResponse responseExep = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .cursorQuery(exchangePositions.get(exchangePositions.size() - 1).getPosition())
            .limitQuery(1)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetExchangePositionsResponse.class));
//        //проверяем, данные в ответе метода и найденную биржевую позицию по условию из БД
        assertThat("NextCursor не равно", responseExep.getNextCursor(), is(nullValue()));
        assertThat("hasNext не равно", false, is(responseExep.getHasNext()));
    }

    @Test
    @AllureId("1757792")
    @DisplayName("1757792.GetExchangePositions.Получение биржевых позиций, добавленных в автоследование")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1757792() {
        List<ExchangePosition> exchangePositions = exchangePositionService.getExchangePositionOrderByTickerAndTraAndTradingClearingAccount();

        //вызываем метод GetExchangePositions
        GetExchangePositionsResponse responseEx = exchangePositionApiAdminCreator.get().getExchangePositions()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetExchangePositionsResponse.class));
        //кастыль
        String orderQuantityLimitsPeriod = "{"+responseEx.getItems().get(0).getOrderQuantityLimits().get(0).getPeriodId() + "="+ responseEx.getItems().get(0).getOrderQuantityLimits().get(0).getLimit().toString()+"}";


        //проверяем, что пришло в ответ от метода createExchangePosition
        assertThat("ticker не равен", responseEx.getItems().get(0).getTicker(), is(exchangePositions.get(0).getTicker()));
        assertThat("tradingClearingAccount не равен", responseEx.getItems().get(0).getTradingClearingAccount(), is(exchangePositions.get(0).getTradingClearingAccount()));
        assertThat("exchange не равен", responseEx.getItems().get(0).getExchange().toString(), is(exchangePositions.get(0).getExchangePositionExchange().toString()));
        assertThat("trackingAllowed не равен", responseEx.getItems().get(0).getTrackingAllowed(), is(exchangePositions.get(0).getTrackingAllowed()));
        assertThat("dailyQuantityLimit не равен", responseEx.getItems().get(0).getDailyQuantityLimit(), is(exchangePositions.get(0).getDailyQuantityLimit()));

        assertThat("orderQuantityLimits не равен", orderQuantityLimitsPeriod,
                                                                        is(exchangePositions.get(0).getOrderQuantityLimits().toString()));

  //      assertThat("periodId не равен", responseEx.getItems().get(0).ge, is(exchangePositions.get(0).getDynamicLimits()));
        assertThat("dynamicLimits не равен", responseEx.getItems().get(0).getDynamicLimits(), is(exchangePositions.get(0).getDynamicLimits()));

    }






//    @Test
//    @MethodSource("provideLimit")
//    @AllureId("1048483")
//    @DisplayName("C1048483.GetExchangePositions.Получение биржевых позиций, добавленных в автоследование, проверка max-limit и default-limit")
//    @Subfeature("Успешные сценарии")
//    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
//    void C1048483() {
//        //создаем инструмент в tracking.exchange_position
//        Map<String, String> listPos = new HashMap<>();
//        try {
//            for (int i = 0; i < 80; i++) {
//                String ticker = "TEST" + Integer.toString(i);
//                String tradingClearingAccount = "L01+00000SPB";
//                steps.createExchangePosition(ticker, tradingClearingAccount, ExchangePositionExchange.SPB, null, null);
//                listPos.put(ticker, tradingClearingAccount);
//            }
//            //вызываем метод getExchangePositions
//            GetExchangePositionsResponse responseExep = exchangePositionApi.getExchangePositions()
//                .reqSpec(r -> r.addHeader("api-key", "tracking"))
//                .xAppNameHeader("invest")
//                .limitQuery(101)
//                .xTcsLoginHeader("tracking_admin")
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(GetExchangePositionsResponse.class));
//            //проверяем, данные в сообщении
//            assertThat("Количество возвращаемых записей не равно", responseExep.getItems().size(), is(100));
//        } finally {
//            listPos.forEach((key, value) -> exchangePositionService.deleteExchangePositionsByKey(key, value));
//        }
//    }
    //методы для работы тестов:********************************************************************

    //body запроса метода updateExchangePosition обязательные парамерты
    public CreateExchangePositionRequest createBodyRequestRequiredParam(String ticker, String tradingClearingAccount, Integer limit, String period,
                                                                        Exchange exchange, Boolean trackingAllowed, int dailyQuantityLimit, Boolean dynamicLimits) {
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
        createExPosition.setDailyQuantityLimit(dailyQuantityLimit);
        createExPosition.setDynamicLimits(dynamicLimits);
        return createExPosition;
    }

}
