package stpTrackingApi.getStrategiesCatalogTabs;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
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
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetStrategiesCatalogTabsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getStrategiesCatalogTabs - Получение вкладок для фильтрации в каталоге стратегий")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getStrategiesCatalogTabs")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})

public class getStrategiesCatalogTabsTest {

    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;

    String siebelId;


    @BeforeAll
    void conf() {
        siebelId = stpSiebel.siebelIdApiMaster;
    }

    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(null, "5.1", "android"),
            Arguments.of("invest", null, "android"),
            Arguments.of("invest", "5.1", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("1500852")
    @DisplayName("1500852.getStrategiesCatalogTab. Не передан один из обязательных параметров")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список возможных вкладок (табов) для фильтрации в каталоге торговых стратегий.")
    void C1500852(String name, String version, String platform) {
        //вызываем метод получения вкладок для фильтрации
        StrategyApi.GetStrategiesCatalogTabsOper getStrategiesCatalogTabs = strategyApiCreator.get().getStrategiesCatalogTabs()
            .xTcsSiebelIdHeader(siebelId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getStrategiesCatalogTabs = getStrategiesCatalogTabs.xAppNameHeader(name);
        }
        if (version != null) {
            getStrategiesCatalogTabs = getStrategiesCatalogTabs.xAppVersionHeader(version);
        }
        if (platform != null) {
            getStrategiesCatalogTabs = getStrategiesCatalogTabs.xPlatformHeader(platform);
        }
        //проверяем тело ответа
        getStrategiesCatalogTabs.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategiesCatalogTabs.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));

    }

    @SneakyThrows
    @Test
    @AllureId("1500855")
    @DisplayName("1500855.getStrategiesCatalogTab. Не передан x-tcs-siebel-id")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список возможных вкладок (табов) для фильтрации в каталоге торговых стратегий.")
    void C1500855() {
        StrategyApi.GetStrategiesCatalogTabsOper getStrategiesCatalogTabs = strategyApiCreator.get().getStrategiesCatalogTabs()
            .xPlatformHeader("ios")
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .respSpec(spec -> spec.expectStatusCode(401));
        //получаем ответ и проверяем errorCode и Error ошибки
        getStrategiesCatalogTabs.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategiesCatalogTabs.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }

    @SneakyThrows
    @Test
    @AllureId("1501045")
    @DisplayName("1501045.getStrategiesCatalogTab. Клиент не найден в Сервисе Счетов")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список возможных вкладок (табов) для фильтрации в каталоге торговых стратегий.")
    void C1501045() {
        StrategyApi.GetStrategiesCatalogTabsOper getStrategiesCatalogTabs = strategyApiCreator.get().getStrategiesCatalogTabs()
            .xTcsSiebelIdHeader("1-LQB8FKN")
            .xPlatformHeader("ios")
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .respSpec(spec -> spec.expectStatusCode(422));
        //проверяем тело ответа
        getStrategiesCatalogTabs.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategiesCatalogTabs.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1500830")
    @DisplayName("1500830.getStrategiesCatalogTab. Получение вкладок для фильтрации")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список возможных вкладок (табов) для фильтрации в каталоге торговых стратегий.")
    void C1500830() {
        GetStrategiesCatalogTabsResponse getStrategiesCatalogTabs = strategyApiCreator.get().getStrategiesCatalogTabs()
            .xTcsSiebelIdHeader(siebelId)
            .xPlatformHeader("ios")
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogTabsResponse.class));
        //Проверяем ответ
        assertThat("Первый tabId не равен", getStrategiesCatalogTabs.getItems().get(0).getTabId(),
            is("rub-currency"));
        assertThat("Первый title не равен", getStrategiesCatalogTabs.getItems().get(0).getTitle(),
            is("Рублевые"));
        assertThat("количество табов не равно", getStrategiesCatalogTabs.getItems().size(),
            is(5));
    }
}
