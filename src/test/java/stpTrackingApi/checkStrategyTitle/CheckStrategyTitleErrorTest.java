package stpTrackingApi.checkStrategyTitle;

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
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.CheckStrategyTitleRequest;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("checkStrategyTitle - Проверка доступности названия для стратегии")
@Feature("TAP-10732")
@Owner("ext.ebelyaninov")
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("checkStrategyTitle")})
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

public class CheckStrategyTitleErrorTest {

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;


    String SIEBEL_ID;
    String title = "Самый уникаЛьный и неповторим!";
    String traceId = "5b23a9529c0f48bc5b23a9529c0f48bc";
    LocalDateTime currentDate = (LocalDateTime.now());
    String contractId;
    Client client;

    UUID investId;


    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID = stpSiebel.siebelIdApiMaster;
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

    private static Stream<Arguments> provideStringsForHeadersCheckTitle() {
        return Stream.of(
            Arguments.of(null, "android", "5.0.1", "Самый уникаЛьный и неповторим!"),
            Arguments.of("trading-invest", null, "4.5.6", "Самый уникаЛьный и неповторим!"),
            Arguments.of("trading", "ios", null, "Самый уникаЛьный и неповторим!"),
            Arguments.of("trading", "ios", null, null),
            Arguments.of("trading", "ios", null, "")
        );
    }

    @Test
    @AllureId("1184393")
    @DisplayName("С1184393.CheckStrategyTitle. Параметр title > 30 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для проверки названия стратегии перед его фиксацией: валидно ли оно для использования и не занято ли")
    void C1184393() {
        String title = "Самый уникаЛьный и неповторим!+";
        //Создаем клиента
        createClient(investId, ClientStatusType.registered, null);

        CheckStrategyTitleRequest request = new CheckStrategyTitleRequest()
            .title(title);

        Response checkStrategyTitle = strategyApiCreator.get().checkStrategyTitle()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .body(request)
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);

        assertThat("isAvailable != false", checkStrategyTitle.getBody().jsonPath().get("isAvailable"), equalTo(false));
    }


    @Test
    @AllureId("1184398")
    @DisplayName("С1184398.CheckStrategyTitle. Не передан заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для проверки названия стратегии перед его фиксацией: валидно ли оно для использования и не занято ли")
    void C1184398() {
        //Создаем клиента
        createClient(investId, ClientStatusType.registered, null);

        CheckStrategyTitleRequest request = new CheckStrategyTitleRequest()
            .title(title);

        Response checkStrategyTitle = strategyApiCreator.get().checkStrategyTitle()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .body(request)
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);

        assertThat("errorCode != InsufficientPrivileges", checkStrategyTitle.getBody().jsonPath().get("errorCode"), equalTo("InsufficientPrivileges"));
        assertThat("errorMessage != Недостаточно прав", checkStrategyTitle.getBody().jsonPath().get("errorMessage"), equalTo("Недостаточно прав"));
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCheckTitle")
    @AllureId("1184389")
    @DisplayName("1184389.CheckStrategyTitle. Валидация запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для проверки названия стратегии перед его фиксацией: валидно ли оно для использования и не занято ли")
    void C1184389(String name, String platform, String version, String title) {
        //Создаем клиента
        createClient(investId, ClientStatusType.registered, null);
        CheckStrategyTitleRequest request = new CheckStrategyTitleRequest()
            .title(title);
        StrategyApi.CheckStrategyTitleOper checkStrategyTitle = strategyApiCreator.get().checkStrategyTitle()
            .body(request)
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            checkStrategyTitle = checkStrategyTitle.xAppNameHeader(name);
        }
        if (version != null) {
            checkStrategyTitle = checkStrategyTitle.xAppVersionHeader(version);
        }
        if (platform != null) {
            checkStrategyTitle = checkStrategyTitle.xPlatformHeader(platform);
        }

        Response checkStrategyTitleResponse = checkStrategyTitle.execute(response -> response);

        assertThat("errorCode != Error", checkStrategyTitleResponse.getBody().jsonPath().get("errorCode"), equalTo("Error"));
        assertThat("errorMessage != Сервис временно недоступен", checkStrategyTitleResponse.getBody().jsonPath().get("errorMessage"), equalTo("Сервис временно недоступен"));
    }


    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
    }
}
