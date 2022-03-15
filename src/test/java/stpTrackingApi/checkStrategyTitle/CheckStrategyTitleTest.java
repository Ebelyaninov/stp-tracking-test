package stpTrackingApi.checkStrategyTitle;


import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.StrategyApiCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.CheckStrategyTitleRequest;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("checkStrategyTitle - Проверка доступности названия для стратегии")
@Feature("TAP-10732")
@Owner("ext.ebelyaninov")
@Tags({@Tag("stp-tracking-api"), @Tag("checkStrategyTitle")})
@DisplayName("stp-tracking-api")
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

public class CheckStrategyTitleTest {
    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ContractService contractService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;

    Contract contract;
    Strategy strategy;
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
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(stpSiebel.siebelIdApiMaster);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {}
            try {
                contractService.deleteContract(contract);
            } catch (Exception e) {}
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {}
        });
    }

    @Test
    @AllureId("1184388")
    @DisplayName("C1184388.CheckStrategyTitle. Не нашли title в таблице strategy")
    @Subfeature("Успешные сценарии")
    @Description("Метод предназначен для проверки названия стратегии перед его фиксацией: валидно ли оно для использования и не занято ли")
    void C1184388() {
        //Создаем клиента
        createClient(investId, ClientStatusType.registered, null);
        //Ищем стратегию с lowerTitle и удаляем, если нашли.
        Strategy getStrategyByLowerTitle = strategyService.getStrategyByLowerTitle(title);

        try {
        if (getStrategyByLowerTitle.getTitle().contains(title)){
            strategyService.deleteStrategy(getStrategyByLowerTitle);
        }
        } catch (Exception e) {}

        Response checkStrategyTitleResponse = checkStrategyTitle(SIEBEL_ID, title, traceId);
        assertThat("isAvailable != true", checkStrategyTitleResponse.getBody().jsonPath().get("isAvailable"), equalTo(true));
    }


    @Test
    @AllureId("1184392")
    @DisplayName("C1184392.CheckStrategyTitle title уже существует в таблице strategy")
    @Subfeature("Успешные сценарии")
    @Description("Метод предназначен для проверки названия стратегии перед его фиксацией: валидно ли оно для использования и не занято ли")
    void C1184392() {
        //Создаем клиента
        createClient(investId, ClientStatusType.registered, null);

       Contract insertContract = new Contract()
            .setId(contractId)
            .setClientId(investId)
//            .setRole(null)
            .setState(ContractState.untracked)
            .setStrategyId(null)
            .setBlocked(false);

        contract = contractService.saveContract(insertContract);

        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy());

        strategy = new Strategy()
            .setId(UUID.fromString("eff82a34-8b9e-4730-9ecc-7dab4b2a5412"))
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(StrategyCurrency.rub)
            .setRiskProfile(StrategyRiskProfile.conservative)
            .setDescription("Test")
            .setStatus(StrategyStatus.draft)
            .setSlavesCount(0)
            .setActivationTime(null)
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategy = trackingService.saveStrategy(strategy);

        Response checkStrategyTitleResponse = checkStrategyTitle(SIEBEL_ID, title, traceId);
        assertThat("isAvailable != true", checkStrategyTitleResponse.getBody().jsonPath().get("isAvailable"), equalTo(false));
        //Изменили статус стратегии на "active" и повторно вызвать метод
        strategy = trackingService.saveStrategy(strategy
            .setStatus(StrategyStatus.active)
            .setActivationTime(currentDate));
        Response checkStrategyTitleResponseWithStatusActive = checkStrategyTitle(SIEBEL_ID, title, traceId);
        assertThat("isAvailable != true", checkStrategyTitleResponseWithStatusActive.getBody().jsonPath().get("isAvailable"), equalTo(false));
    }


    @Test
    @AllureId("1184394")
    @DisplayName("C1184394.Проверка хедера x-trace-id")
    @Subfeature("Успешные сценарии")
    @Description("Метод предназначен для проверки названия стратегии перед его фиксацией: валидно ли оно для использования и не занято ли")
    void C1184394() {
        //Создаем клиента
        createClient(investId, ClientStatusType.registered, null);

        Response checkStrategyTitle = strategyApiCreator.get().checkStrategyTitle()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .body(new CheckStrategyTitleRequest()
                .title(title))
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);

        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH");
        String dateNow = (formatter.format(dateNowOne));

        assertThat("Не получили header x-trace-id в ответе", checkStrategyTitle.getHeaders().getValue("x-trace-id"), notNullValue());
        assertThat("time !=  " + dateNow, checkStrategyTitle.getHeaders().getValue("x-server-time").substring(0, 13), equalTo(dateNow));
        //повторно вызываем метод, для проверки header  xB3TraceidHeader
        Response getAuthorizedClientResponseWithTraceId = checkStrategyTitle(SIEBEL_ID, traceId, traceId);
        assertThat("x-trace-id != входному параметру x-b3-traceid", getAuthorizedClientResponseWithTraceId.getHeaders().getValue("x-trace-id"), equalTo(traceId));
    }

    //*** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
    }

    @Step("Вызов метода CheckStrategyTitle")
    Response checkStrategyTitle (String siebelId, String title, String traceId) {
        CheckStrategyTitleRequest request = new CheckStrategyTitleRequest()
            .title(title);

        Response checkStrategyTitle = strategyApiCreator.get().checkStrategyTitle()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .body(request)
            .xTcsSiebelIdHeader(siebelId)
            .xB3ParentspanidHeader("a2fb4a1d1a96d312")
            .xB3SampledHeader("1")
            .xB3SpanidHeader("a2fb4a1d1a96d312")
            .xB3TraceidHeader(traceId)
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        return checkStrategyTitle;
    }
}
