package stpTrackingAdminApi.activateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;

import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})

@Epic("activateStrategy -  Активация стратегии")
@Feature("TAP-6815")
@Service("stp-tracking-admin")
@Subfeature("Альтернативные сценарии")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class
})
public class ActivateStrategyErrorTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();


    static final String SIEBEL_ID = "5-55RUONV5";
    String xApiKey = "x-api-key";
    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    BillingService billingService;
    @Autowired
    StpTrackingAdminSteps steps;

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
    @AllureId("C457266")
    @DisplayName("C457266.ActivateStrategy. Не переданы обязательные параметры")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C457266() {
        String title = "Тест стратегия 101 Autotest - Заголовок";
        String description = "New Test Strategy 101 Autotest - Описание";
        Integer score = 1;
        UUID strategyId = UUID.randomUUID();
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);
        //Вызываем ActiveStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
    }


    @Test
    @AllureId("457269")
    @DisplayName("C457269.ActivateStrategy. Валидация запроса: не корректный формат strategyId")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457269() {
        //Вызываем метод activateStrategy с некоррентным значением strategyId (не формат UUID)
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("1111234")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
    }


    @Test
    @AllureId("457270")
    @DisplayName("C457270.ActivateStrategy. Авторизация по api-key, передаем неверное значение")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457270() {
        String title = "Тест стратегия 102 Autotest - Заголовок";
        String description = "New Test Strategy 102 Autotest - Описание";
        Integer score = 1;
        UUID strategyId = UUID.randomUUID();
        //Получаем данные по клиенту в API-сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);
        //Вызываем метод activateStrategy с некоррентным значением api-key
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "trackinnng"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }


    @Test
    @AllureId("457271")
    @DisplayName("C457271.ActivateStrategy. Авторизация по api-key, не передаем значение")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457271() {
        String title = "Тест стратегия 103 Autotest - Заголовок";
        String description = "New Test Strategy 103 Autotest - Описание";
        Integer score = 1;
        UUID strategyId = UUID.randomUUID();
        //Получаем данные по клиенту в API-сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);
        //Вызываем метод activateStrategy без api-key
        strategyApi.activateStrategy()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("457272")
    @DisplayName("C457272.ActivateStrategy. Передаем несуществующий strategyId")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457272() {
        //Вызываем метод activateStrategy с несуществующим значением strategyId
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("85d42f98-cd5e-4bb6-82be-d46e722b8888")
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
    }


    @Test
    @AllureId("840992")
    @DisplayName("C840992.ActivateStrategy. Проверка ограниченйи параметров при Активация стретегии, score = null")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C840992() {
        String title = "Тест стратегия 104 Autotest - Заголовок";
        String description = "New Test Strategy 104 Autotest - Описание";
        Integer score = null;
        UUID strategyId = UUID.randomUUID();
        //Получаем данные по клиенту в API-сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft, при этом score передаем как null
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);
        //Вызываем метод activateStrategy без api-key
        strategyApi.activateStrategy()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }
}
