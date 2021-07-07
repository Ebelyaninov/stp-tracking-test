package stpTrackingAdminApi.getExchangePositions;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetExchangePositionsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.ExchangePosition;
import ru.qa.tinkoff.tracking.entities.enums.ExchangePositionExchange;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;


@Slf4j
@Epic("getExchangePositions -  Получение биржевых позиций, добавленных в автоследование")
@Feature("TAP-10351")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SptTrackingAdminStepsConfiguration.class
})
public class GetExchangePositionsTest {
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    ExchangePositionService exchangePositionService;
    ExchangePositionApi exchangePositionApi = ApiClient.api(ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;

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

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersGetExchangePosition")
    @AllureId("1041093")
    @DisplayName("C1041093.GetExchangePositions.Валидация запроса: обязательные параметры в headers: x-app-name, x-tcs-login")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод необходим для получения списка всех позиций, участвующих в автоследовании.")
    void C1041093(String name, String login) {
        ExchangePositionApi.GetExchangePositionsOper getExchangePositions = exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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
        exchangePositionApi.getExchangePositions()
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
        exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "trading"))
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
        GetExchangePositionsResponse responseExep = exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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
        GetExchangePositionsResponse responseExep = exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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
        GetExchangePositionsResponse responseExep = exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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
        GetExchangePositionsResponse responseEx = exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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
        GetExchangePositionsResponse responseExep = exchangePositionApi.getExchangePositions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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


}
