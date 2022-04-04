package stpTrackingApi.getPositionRetentions;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.model.GetPositionRetentionsResponse;
import ru.qa.tinkoff.swagger.tracking.model.PositionRetention;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;

import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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


public class getPositionRetentionsSuccessTest {
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<AnalyticsApi> analyticsApiCreator;

    String SIEBEL_ID;

    @BeforeAll
    void conf() {
        SIEBEL_ID = stpSiebel.siebelIdApiMaster;
    }

    @SneakyThrows
    @Test
    @AllureId("891716")
    @DisplayName("C891716.GetPositionRetentions. Получение списка возможного времени удержания позиции")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void C891716() {
        GetPositionRetentionsResponse actualResponse = analyticsApiCreator.get().getPositionRetentions()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("0.0.1")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("autotest")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetPositionRetentionsResponse.class));
        GetPositionRetentionsResponse expectedResponse = createExpectedGetPositionRetentionsResponse();
        step("Проверка полученного ответа с ожидаемымы ответом", () -> {
            assertThat("Полученный Response совпадает с ожидаемым", actualResponse, is(equalTo(expectedResponse)));
        });
    }

    GetPositionRetentionsResponse createExpectedGetPositionRetentionsResponse() {
        GetPositionRetentionsResponse expectedResponse = new GetPositionRetentionsResponse();
        List<PositionRetention> expectedItems = new ArrayList<>();
        PositionRetention entity1 = new PositionRetention();
        entity1.setPositionRetentionId("days");
        entity1.setTitle("до дня");
        PositionRetention entity2 = new PositionRetention();
        entity2.setPositionRetentionId("weeks");
        entity2.setTitle("до недели");
        PositionRetention entity3 = new PositionRetention();
        entity3.setPositionRetentionId("months");
        entity3.setTitle("до месяца");
        PositionRetention entity4 = new PositionRetention();
        entity4.setPositionRetentionId("forever");
        entity4.setTitle("больше месяца");
        expectedItems.add(entity1);
        expectedItems.add(entity2);
        expectedItems.add(entity3);
        expectedItems.add(entity4);
        expectedResponse.setItems(expectedItems);
        return expectedResponse;
    }
}