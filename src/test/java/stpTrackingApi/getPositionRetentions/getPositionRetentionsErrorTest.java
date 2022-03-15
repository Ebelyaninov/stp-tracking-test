package stpTrackingApi.getPositionRetentions;


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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.AnalyticsApiCreator;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;

import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;


@Epic("getPositionRetentions")
@Feature("TAP-10862")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getPositionRetentions")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})

public class getPositionRetentionsErrorTest {
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<AnalyticsApi> analyticsApiCreator;

//    AnalyticsApi analyticsApi;

    String SIEBEL_ID;
    String FAKE_SIEBEL_ID = "5-AABBCCDD";

    @BeforeAll
    void conf() {
//        analyticsApi = ApiClient.api(ApiClient.Config.apiConfig()).analytics();
        SIEBEL_ID = stpSiebel.siebelIdApiMaster;
    }

    @SneakyThrows
    @Test
    @AllureId("С891771")
    @DisplayName("С891771.GetPositionRetentions. Недостаточно прав, не передан заголовок 'X-TCS-SIEBEL-ID'")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void C891771() {
        AnalyticsApi.GetPositionRetentionsOper getPositionRetentions = analyticsApiCreator.get().getPositionRetentions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xDeviceIdHeader("autotest")
            .xAppNameHeader("invest")
            .xPlatformHeader("ios")
            .xAppVersionHeader("0.0.1")
            .respSpec(spec -> spec.expectStatusCode(401));
//        String responseBodyData = getPositionRetentions.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getPositionRetentions.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
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
        AnalyticsApi.GetPositionRetentionsOper getPositionRetentions = analyticsApiCreator.get().getPositionRetentions()
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
        JSONObject jsonObject = new JSONObject(getPositionRetentions.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("937560")
    @DisplayName("C937560.GetPositionRetentions. В заголовке 'X-TCS-SIEBEL-ID' передан не существующий {SIEBEL_ID}")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void C937560() {
        AnalyticsApi.GetPositionRetentionsOper getPositionRetentions = analyticsApiCreator.get().getPositionRetentions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xTcsSiebelIdHeader(FAKE_SIEBEL_ID)
            .xDeviceIdHeader("autotest")
            .xAppNameHeader("invest")
            .xPlatformHeader("ios")
            .xAppVersionHeader("0.0.1")
            .respSpec(spec -> spec.expectStatusCode(422));
        JSONObject jsonObject = new JSONObject(getPositionRetentions.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }
}