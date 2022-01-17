package stpTrackingAdminApi.getSignals;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("getSignals - Получение списка сигналов на стратегии")
@Feature("TAP-13486")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Subfeature("Альтернативные сценарии")
@Tags({@Tag("stp-tracking-admin"), @Tag("getSignals")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class
})
public class GetSignalsErrorTest {

    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps stpTrackingAdminSteps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    MasterSignalDao masterSignalDao;

    String xApiKey = "x-api-key";
    String key= "tracking";


    SignalApi signalApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ApiClient.Config.apiConfig()).signal();

    String siebelIdMaster = "5-DYNN1E3S";
    String contractIdMaster;
    UUID strategyId;
    LocalDateTime localDateTime;
    UUID investIdMaster;

    @BeforeAll
    void getDataFromAccount(){
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = stpTrackingAdminSteps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
    }

    @BeforeEach
    void createClient() {
        strategyId = UUID.randomUUID();
        localDateTime = LocalDateTime.now();
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategyNew(siebelIdMaster, investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(1));
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                strategyService.deleteStrategy(stpTrackingAdminSteps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(stpTrackingAdminSteps.contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(stpTrackingAdminSteps.client);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1458359")
    @DisplayName("C1458359.GetSignals.Не нашли стратегию")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458359() {

        strategyService.deleteStrategy(stpTrackingAdminSteps.strategy);

        ErrorResponse errorResponse =  getSignalsResponse(strategyId, 422);
        checkErrorFromResponce(errorResponse, "0344-13-B01", "Стратегия не найдена");
    }

    @SneakyThrows
    @Test
    @AllureId("1458346")
    @DisplayName("C1458346.GetSignals. Заголовок X-API-KEY не передан")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458346() {

        signalApi.getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("login")
            .respSpec(spec -> spec.expectStatusCode(401));
    }

    private static Stream<Arguments> provideStringsForSubscriptionStatus() {
        return Stream.of(
            Arguments.of(null, "xAppName"),
            Arguments.of("login", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForSubscriptionStatus")
    @AllureId("1458361")
    @DisplayName("C1458361.GetSignals. Валидация запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458361(String login, String xAppName) {

        strategyService.deleteStrategy(stpTrackingAdminSteps.strategy);

        SignalApi.GetSignalsOper updateGetSignals = signalApi.getSignals()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .strategyIdPath(strategyId)
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(400));
        if (login != null) {
            updateGetSignals = updateGetSignals.xAppNameHeader(login);
        }
        if (xAppName != null) {
            updateGetSignals = updateGetSignals.xTcsLoginHeader(xAppName);
        }
        updateGetSignals.execute(ResponseBodyData::asString);

        JSONObject jsonObject = new JSONObject(updateGetSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    void checkErrorFromResponce (ErrorResponse getSignalsResponse, String errorCode, String errorMessage){
        assertThat("код ошибки не равно", getSignalsResponse.getErrorCode(), is(errorCode));
        assertThat("Сообщение об ошибке не равно", getSignalsResponse.getErrorMessage(), is(errorMessage));
    }


    ErrorResponse getSignalsResponse (UUID strategyId, int statusCode) {
        ErrorResponse getSignalsResponse = signalApi.getSignals()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("login")
            .respSpec(spec -> spec.expectStatusCode(statusCode))
            .execute(response -> response.as(ErrorResponse.class));
        return getSignalsResponse;
    }
}
