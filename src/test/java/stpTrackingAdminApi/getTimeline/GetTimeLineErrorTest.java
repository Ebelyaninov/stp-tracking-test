package stpTrackingAdminApi.getTimeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveAdjust;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.TimelineApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetTimelineRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetTimelineResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getTimeline - Получение ленты событий")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("getTimeline")})
@Subfeature("Альтернативные сценарии")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    KafkaOldConfiguration.class
})

public class GetTimeLineErrorTest {

    TimelineApi timelineApi = ApiClient.api(ApiClient.Config.apiConfig()).timeline();

    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    StpTrackingSlaveSteps slaveSteps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    StpSiebel siebel;

    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";


    String contractIdMaster;
    String contractIdSlave;
    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;

    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdMasterAdmin);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebel.siebelIdSlaveAdmin);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("1584955")
    @DisplayName("C1584955.GetTimeline.НЕ находим запрашиваемую стратегию")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1584955() {
        strategyId = UUID.randomUUID();
        GetTimelineRequest request = createBody(strategyId, contractIdSlave);
        //вызываем метод getTimeline
        ErrorResponse errorResponse = steps.getimelineWithError(request, 422);
        checkErrorFromResponce(errorResponse, "0344-17-B01", "Стратегия не найдена");
    }

    @Test
    @AllureId("1584944")
    @DisplayName("C1584944.GetTimeline. Значение параметра cursor НЕ является числом")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1584944() {
        strategyId = UUID.randomUUID();
        GetTimelineRequest request = createBody(strategyId, contractIdSlave);
        //вызываем метод getTimeline
        ErrorResponse errorResponse = timelineApi.getTimeline()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .cursorQuery("CursorIsNotNumber")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse.class));;
        checkErrorFromResponce(errorResponse, "0344-17-V12", "Сервис временно недоступен");
    }

    @Test
    @AllureId("1584716")
    @DisplayName("C1584716.GetTimeline. Заголовок X-API-KEY не передан")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1584716() {
        strategyId = UUID.randomUUID();
        GetTimelineRequest request = createBody(strategyId, contractIdSlave);
        //вызываем метод getTimeline
        timelineApi.getTimeline()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(401));
    }

    @Test
    @AllureId("1705744")
    @DisplayName("C1705744.GetTimeline. Заголовок X-API-KEY не передан")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1705744() {
        strategyId = UUID.randomUUID();
        GetTimelineRequest request = createBody(strategyId, contractIdSlave);
        //вызываем метод getTimeline
        timelineApi.getTimeline()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(401));
    }

    void checkErrorFromResponce (ErrorResponse errorResponse, String errorCode, String errorMessage){
        assertThat("код ошибки не равно" + errorCode, errorResponse.getErrorCode(), is(errorCode));
        assertThat("Сообщение об ошибке не равно" + errorMessage, errorResponse.getErrorMessage(), is(errorMessage));
    }

    GetTimelineRequest createBody (UUID strategyId, String contractI){
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractI);
        return request;
    }
}
