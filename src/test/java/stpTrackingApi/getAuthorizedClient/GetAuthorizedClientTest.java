package stpTrackingApi.getAuthorizedClient;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
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
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.tracking.api.ClientApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.social.entities.SocialProfile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    StpTrackingApiStepsConfiguration.class

})

public class GetAuthorizedClientTest {

    ClientApi clientApi = ru.qa.tinkoff.swagger.tracking.invoker.ApiClient
        .api((ApiClient.Config.apiConfig())).client();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;

    String SIEBEL_ID = "5-192WBUXCI";
    String traceId = "5b23a9529c0f48bc5b23a9529c0f48bc";
    String contractId;
    Client client;

    UUID investId;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
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

    private static Stream<Arguments> provideMasterStatusForCLient () {
        return Stream.of(
            Arguments.of(ClientStatusType.none),
            Arguments.of(ClientStatusType.registered),
            Arguments.of(ClientStatusType.confirmed)
        );
    }

    @ParameterizedTest
    @MethodSource("provideMasterStatusForCLient")
    @AllureId("1131949")
    @DisplayName("C1131949.getAuthorizedClient.Получение status = none \\ registed \\ confirmed из client.master_status")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения базовых данных авторизованного клиента в разрезе статуса его участия в автоследовании.")
    void C1131949(ClientStatusType masterStatus) {
        //Создаем клиента в табл. client
        createClient(investId, masterStatus, null);
        Response getAuthorizedClientResponse = getAuthorizedClient(SIEBEL_ID, traceId);
        assertThat("master.status != registered", getAuthorizedClientResponse.getBody().jsonPath().get("master.status").toString(), equalTo(masterStatus.toString()));
    }

    @Test
    @AllureId("1131988")
    @DisplayName("C1131988.getAuthorizedClient.  Получение данных авторизованного клиента без записи в таблице client\n")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения базовых данных авторизованного клиента в разрезе статуса его участия в автоследовании.")
    void C1131988() {
        Response getAuthorizedClientResponse = getAuthorizedClient(SIEBEL_ID, traceId);
        assertThat("master.status != registered", getAuthorizedClientResponse.getBody().jsonPath().get("master.status").toString(), equalTo(ClientStatusType.none.toString()));
    }

    @Test
    @AllureId("1131992")
    @DisplayName("C1131992.getAuthorizedClient. Заполнение хедера x-b3-traceid входным параметром и master_status == none")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения базовых данных авторизованного клиента в разрезе статуса его участия в автоследовании.")
    void C1131992() {
        createClient(investId, ClientStatusType.none, null);
        //Вызов метода без traceId
        Response getAuthorizedClientResponse = clientApi.getAuthorizedClient()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH");
        String dateNow = (formatter.format(dateNowOne));
        assertThat("master.status != none", getAuthorizedClientResponse.getBody().jsonPath().get("master.status").toString(), equalTo(ClientStatusType.none.toString()));
        assertThat("time !=  " + dateNow, getAuthorizedClientResponse.getHeaders().getValue("x-server-time").substring(0, 13), equalTo(dateNow));
        assertThat("Не получили header x-trace-id в ответе", getAuthorizedClientResponse.getHeaders().getValue("x-trace-id"), notNullValue());
        //повторно вызываем метод, для проверки header  xB3TraceidHeader
        Response getAuthorizedClientResponseWithTraceId = getAuthorizedClient(SIEBEL_ID, traceId);
        assertThat("x-trace-id != входному параметру x-b3-traceid", getAuthorizedClientResponseWithTraceId.getHeaders().getValue("x-trace-id"), equalTo(traceId));
    }



    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
    }

    @Step("Вызов метода getAuthorizedClient")
    Response getAuthorizedClient (String siebelId, String traceId) {
        Response getAuthorizedClientResponse = clientApi.getAuthorizedClient()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelId)
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        return getAuthorizedClientResponse;
    }
}
