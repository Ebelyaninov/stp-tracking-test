package stpTrackingApi.getAuthorizedClient;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.ClientApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;

import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("getAuthorizedClient - Метод получения данных авторизованного клиента")
@Feature("TAP-6645")
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getAuthorizedClient")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})

public class GetAuthorizedClientErrorTest {
    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StpSiebel stpSiebel;
    String SIEBEL_ID;
    String traceId = "5b23a9529c0f48bc5b23a9529c0f48bc";
    String contractId;
    Client client;
    UUID investId;
    @Autowired
    ApiCreator<ClientApi> clientApiCreator;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID = stpSiebel.siebelIdApiMaster;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createEventInTrackingContractEvent(contractId);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideStringsForHeadersGetAuthorizedClient() {
        return Stream.of(
            Arguments.of(null, "android", "5.0.1"),
            Arguments.of("trading-invest", null, "4.5.6"),
            Arguments.of("trading", "ios", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersGetAuthorizedClient")
    @AllureId("1131948")
    @DisplayName("С1131948.getAuthorizedClient. Ошибка валидации запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения базовых данных авторизованного клиента в разрезе статуса его участия в автоследовании.")
    void C1131948(String name, String platform, String version) {
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        ClientApi.GetAuthorizedClientOper getAuthorizedClient = clientApiCreator.get().getAuthorizedClient()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getAuthorizedClient = getAuthorizedClient.xAppNameHeader(name);
        }
        if (version != null) {
            getAuthorizedClient = getAuthorizedClient.xAppVersionHeader(version);
        }
        if (platform != null) {
            getAuthorizedClient = getAuthorizedClient.xPlatformHeader(platform);
        }
        Response getAuthorizedClientResponse = getAuthorizedClient.execute(response -> response);
        checkServiceIsTemporarilyUnavailable(getAuthorizedClientResponse);
    }


    @Test
    @AllureId("1131990")
    @DisplayName("C1131990.getAuthorizedClient. Не передали заголовок X-TCS-SIEBEL-ID\n")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения базовых данных авторизованного клиента в разрезе статуса его участия в автоследовании.")
    void C1131990() {
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        Response getAuthorizedClient = clientApiCreator.get().getAuthorizedClient()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
        assertThat("errorCode != Error", getAuthorizedClient.getBody().jsonPath().get("errorCode").toString(), equalTo("InsufficientPrivileges"));
        assertThat("errorMessage != Сервис временно недоступен", getAuthorizedClient.getBody().jsonPath().get("errorMessage").toString(), equalTo("Недостаточно прав"));
    }

    @Test
    @AllureId("1189654")
    @DisplayName("C1189654.getAuthorizedClient. Не нашли пользователя в кэше")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения базовых данных авторизованного клиента в разрезе статуса его участия в автоследовании.")
    void C1189654() {
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        Response getAuthorizedClient = clientApiCreator.get().getAuthorizedClient()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader("5-NotFound")
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        checkServiceIsTemporarilyUnavailable(getAuthorizedClient);
    }

    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
    }

    void checkServiceIsTemporarilyUnavailable(Response getAuthorizedClientResponse) {
        assertThat("errorCode != Error", getAuthorizedClientResponse.getBody().jsonPath().get("errorCode").toString(), equalTo("Error"));
        assertThat("errorMessage != Сервис временно недоступен", getAuthorizedClientResponse.getBody().jsonPath().get("errorMessage").toString(), equalTo("Сервис временно недоступен"));
    }

}
