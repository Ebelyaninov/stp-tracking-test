package stpTrackingAdminApi.getStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetStrategyResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("getStrategy - Получение данных торговой стратегии")
@Feature("TAP-7442")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("getStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class
})
public class GetStrategyTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();


    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    StrategyService strategyService;

    String SIEBEL_ID = "1-1XHHA7S";
    String xApiKey = "x-api-key";
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(steps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.client);
            } catch (Exception e) {
            }
        });
    }

    @Test
    @AllureId("536608")
    @DisplayName("C536608.GetStrategy.Получение данных торговой стратегии стратегия не активна")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C536608() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST", "OwnerTEST");
        Client clientDB = clientService.getClient(investId);
        String nickname = clientDB.getSocialProfile().getNickname();
        String ownerDescription = strategyService.getStrategy(strategyId).getOwnerDescription();
        BigDecimal expectedRelativeYield = strategyService.getStrategy(strategyId).getExpectedRelativeYield();
        Integer score = strategyService.getStrategy(strategyId).getScore();
        //вызываем метод getStrategy
        GetStrategyResponse responseExep = strategyApi.getStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //проверяем, данные в сообщении
        assertThat("Nickname profile не равен", responseExep.getOwner().getSocialProfile().getNickname(), is(nickname));
        assertThat("ownerDescription не равно", responseExep.getOwner().getDescription(), is(ownerDescription));
        assertThat("short_description не равно", responseExep.getShortDescription().toString(), is("TEST"));
        assertThat("expectedRelativeYield не равено", responseExep.getExpectedRelativeYield(), is(expectedRelativeYield));
        assertThat("status не равен", responseExep.getStatus().toString(), is("draft"));
        assertThat("title не равен", responseExep.getTitle(), is(title));
        assertThat("baseCurrency не равен", responseExep.getBaseCurrency().toString(), is("rub"));
        assertThat("riskProfile не равно", responseExep.getRiskProfile().toString(), is("conservative"));
        assertThat("description не равно", responseExep.getDescription(), is(description));
        assertThat("score не равно", responseExep.getScore(), is(score));
        assertThat("feeRate.management не равно", responseExep.getFeeRate().getManagement().toString(), is("0.04") );
    }


    @Test
    @AllureId("536280")
    @DisplayName("C536280.GetStrategy.Получение данных торговой стратегии статегия активна")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C536280() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, expectedRelativeYield, "TEST", "OwnerTEST");
        Client clientDB = clientService.getClient(investId);
        String nickname = clientDB.getSocialProfile().getNickname();
        String ownerDescription = strategyService.getStrategy(strategyId).getOwnerDescription();
        BigDecimal expectedRelativeYield = strategyService.getStrategy(strategyId).getExpectedRelativeYield();
        Integer score = strategyService.getStrategy(strategyId).getScore();
        //вызываем метод getStrategy
        GetStrategyResponse responseExep = strategyApi.getStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //проверяем, данные в сообщении
        assertThat("Nickname profile не равен", responseExep.getOwner().getSocialProfile().getNickname(), is(nickname));
        assertThat("ownerDescription не равно", responseExep.getOwner().getDescription(), is(ownerDescription));
        assertThat("short_description не равно", responseExep.getShortDescription().toString(), is("TEST"));
        assertThat("expectedRelativeYield не равено", responseExep.getExpectedRelativeYield(), is(expectedRelativeYield));
        assertThat("status не равен", responseExep.getStatus().toString(), is("active"));
        assertThat("title не равен", responseExep.getTitle(), is(title));
        assertThat("baseCurrency не равен", responseExep.getBaseCurrency().toString(), is("rub"));
        assertThat("riskProfile не равно", responseExep.getRiskProfile().toString(), is("conservative"));
        assertThat("description не равно", responseExep.getDescription(), is(description));
        assertThat("score не равно", responseExep.getScore(), is(score));
        assertThat("feeRate.management не равно", responseExep.getFeeRate().getManagement().toString(), is("0.04") );
    }


    @Test
    @AllureId("536612")
    @DisplayName("C536612.GetStrategy.Валидация обязательных параметров: x-app-name")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C536612() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, expectedRelativeYield, "TEST", "OwnerTEST");
        //вызываем метод getStrategy
        Response expectedResponse = strategyApi.getStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
    }

    @Test
    @AllureId("536613")
    @DisplayName("C536612.GetStrategy.Авторизация: не передаем apiKey")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C536613() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, expectedRelativeYield, "TEST", "OwnerTEST");
        //вызываем метод getStrategy
        strategyApi.getStrategy()
            .xAppNameHeader("invest")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("536614")
    @DisplayName("C536614.GetStrategy.Авторизация: передаем не правильное значение apiKey")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C536614() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, expectedRelativeYield, "TEST", "OwnerTEST");
        //вызываем метод getStrategy
        strategyApi.getStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "trading"))
            .xAppNameHeader("invest")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("536615")
    @DisplayName("C536615.GetStrategy.Неверное значение стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C536615() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        UUID strategyIdTest = UUID.randomUUID();
        //находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, expectedRelativeYield, "TEST", "OwnerTEST");
        //вызываем метод getStrategy
        Response expectedResponse = strategyApi.getStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .strategyIdPath(strategyIdTest)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
    }
}
