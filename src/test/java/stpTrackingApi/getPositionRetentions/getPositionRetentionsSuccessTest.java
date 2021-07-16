package stpTrackingApi.getPositionRetentions;


import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetPositionRetentionsResponse;
import ru.qa.tinkoff.swagger.tracking.model.PositionRetention;

import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("5885")
@Feature("")
@Subfeature("Успешные сценарии")
@Service("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)


public class getPositionRetentionsSuccessTest {
    AnalyticsApi analyticsApi;

    String SIEBEL_ID = "4-20JTDILL";

    @BeforeAll
    void conf() {
        analyticsApi = ApiClient.api(ApiClient.Config.apiConfig()).analytics();
    }

    @SneakyThrows
    @Test
    @AllureId("891716")
    @DisplayName("C891716.GetPositionRetentions. Получение списка возможного времени удержания позиции")
    @Description("Метод возвращает возможные значения показателя времени удержания позиции на торговой стратегии")
    void C891716() {
        GetPositionRetentionsResponse actualResponse = analyticsApi.getPositionRetentions()
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