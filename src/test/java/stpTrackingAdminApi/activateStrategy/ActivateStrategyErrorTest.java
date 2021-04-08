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
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
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
import ru.tinkoff.trading.tracking.Tracking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("activateStrategy -  Активация стратегии")
@Feature("TAP-6815")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class})
public class ActivateStrategyErrorTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Strategy strategy;
    Client client;
    Contract contract;
    static final String SIEBEL_ID = "5-QFZ2YBCE";

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

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(strategy);
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
    @AllureId("C457266")
    @DisplayName("C457266.ActivateStrategy.Не переданы обязательные параметры")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C457266() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //вызываем ActiveStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
    }


    @Test
    @AllureId("457269")
    @DisplayName("C457269.ActivateStrategy.Валидация запроса: не корректный формат strategyId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457269() {
        //вызываем метод activateStrategy с некоррентным значением strategyId (не формат UUID)
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("1111234")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
    }

    @Test
    @AllureId("457270")
    @DisplayName("C457270.ActivateStrategy.Авторизация по api-key, передаем неверное значение")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457270() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //вызываем метод activateStrategy с некоррентным значением api-key
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "trackissng"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }

    @Test
    @AllureId("457271")
    @DisplayName("C457271.ActivateStrategy.Авторизация по api-key, не передаем значение")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457271() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //вызываем метод activateStrategy без api-key
        strategyApi.activateStrategy()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("457272")
    @DisplayName("C457272.ActivateStrategy.Передаем несуществующий strategyId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457272() {
        //вызываем метод activateStrategy с несуществующим значением strategyId
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("85d42f98-cd5e-4bb6-82be-d46e722b8888")
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
    }


    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, SocialProfile socialProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = contractService.saveContract(contract);

        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date);

        strategy = trackingService.saveStrategy(strategy);
    }
}
