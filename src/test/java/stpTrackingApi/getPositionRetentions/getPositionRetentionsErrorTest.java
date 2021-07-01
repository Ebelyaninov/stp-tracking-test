package stpTrackingApi.getPositionRetentions;


import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;

import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@Slf4j
@Epic("5885")
@Subfeature("Альтернативные сценарии")
@Service("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)


public class getPositionRetentionsErrorTest {
    AnalyticsApi analyticsApi;

    String SIEBEL_ID = "4-20JTDILL";
    String FAKE_SIEBEL_ID = "5-AABBCCDD";

    @BeforeAll
    void conf() {
        analyticsApi = ApiClient.api(ApiClient.Config.apiConfig()).analytics();
    }

    @SneakyThrows
    @Test
    @AllureId("С891771")
    @DisplayName("С891771.GetPositionRetentions. Недостаточно прав, не передан заголовок 'X-TCS-SIEBEL-ID'")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void С891771() {
        AnalyticsApi.GetPositionRetentionsOper getPositionRetentions = analyticsApi.getPositionRetentions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xDeviceIdHeader("autotest")
            .xAppNameHeader("invest")
            .xPlatformHeader("ios")
            .xAppVersionHeader("0.0.1")
            .respSpec(spec -> spec.expectStatusCode(401));
        String responseBodyData = getPositionRetentions.execute(ResponseBodyData::asString);
        step("Проверка тела ответа", () -> {
            assertAll(
                () -> assertThat("Код ошибки - errorCode совпадает с ожидаемым",
                    responseBodyData.contains("0350-00-Z99"), is(true)),
                () -> assertThat("Текст сообщения об ошибке - errorMessage совпадает с ожижаемым",
                    responseBodyData.contains("Недостаточно прав"), is(true)));
        });
    }


    private static Stream<Arguments> stringProviderForHttpHeaders() {
        return Stream.of(
            Arguments.of(null, "android", "0.0.1"),
            Arguments.of("trading-invest", null, "0.0.2"),
            Arguments.of("trading", "iOS 14.5", null));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("stringProviderForHttpHeaders")
    @AllureId("937555")
    @DisplayName("C937555.GetPositionRetentions. Валидация не пройдена, не передан любой из заголовков кроме 'X-DEVICE-ID' и 'X-TCS-SIEBEL-ID'")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void C937555(String appName, String appPlatform, String appVersion) {
        AnalyticsApi.GetPositionRetentionsOper getPositionRetentions = analyticsApi.getPositionRetentions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xDeviceIdHeader("autotest")
            .respSpec(spec -> spec.expectStatusCode(400));
        if (appName != null) {
            getPositionRetentions.xAppNameHeader(appName);
        }
        if (appPlatform != null) {
            getPositionRetentions.xPlatformHeader(appPlatform);
        }
        if (appVersion != null) {
            getPositionRetentions.xAppVersionHeader(appVersion);
        }
        String responseBodyData = getPositionRetentions.execute(ResponseBodyData::asString);
        step("Проверка тела ответа", () -> {
            assertAll(
                () -> assertThat("Код ошибки - errorCode совпадает с ожидаемым",
                    responseBodyData.contains("0350-00-Z99"), is(true)),
                () -> assertThat("Текст сообщения об ошибке - errorMessage совпадает с ожижаемым",
                    responseBodyData.contains("Сервис временно недоступен"), is(true)));
        });
    }


    @SneakyThrows
    @Test
    @AllureId("937560")
    @DisplayName("C937560.GetPositionRetentions. В заголовке 'X-TCS-SIEBEL-ID' передан не существующий {SIEBEL_ID}")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void C937560() {
        AnalyticsApi.GetPositionRetentionsOper getPositionRetentions = analyticsApi.getPositionRetentions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xTcsSiebelIdHeader(FAKE_SIEBEL_ID)
            .xDeviceIdHeader("autotest")
            .xAppNameHeader("invest")
            .xPlatformHeader("ios")
            .xAppVersionHeader("0.0.1")
            .respSpec(spec -> spec.expectStatusCode(422));
        String responseBodyData = getPositionRetentions.execute(ResponseBodyData::asString);
        step("Проверка тела ответа", () -> {
            assertAll(
                () -> assertThat("Код ошибки - errorCode совпадает с ожидаемым",
                    responseBodyData.contains("0350-12-B04"), is(true)),
                () -> assertThat("Текст сообщения об ошибке - errorMessage совпадает с ожижаемым",
                    responseBodyData.contains("Сервис временно недоступен"), is(true)));
        });
    }
}