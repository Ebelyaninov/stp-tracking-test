package stpTrackingAdminApi.updateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequestOwner;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.parameter;
import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Slf4j
@Epic("updateStrategy - Обновление стратегии администратором")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Subfeature("Альтернативные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("updateStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class
})
public class UpdateStrategyAdminErrorTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    Client client;
    Contract contract;
    Strategy strategy;


    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ContractService contractService;
    @Autowired
    ClientService clientService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingAdminSteps steps;


    String SIEBEL_ID = "4-1UBHYQ63";
    String xApiKey = "x-api-key";
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(steps.strategy);
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

    private static Stream<Arguments> provideStringsForHeadersUpdateStrategy() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersUpdateStrategy")
    @AllureId("482567")
    @DisplayName("C482567.UpdateStrategy.Валидация запроса: обязательные параметры (x-app-name, x-tcs-login")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482567(String name, String login) {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 101 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 101 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 101 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            updateStrategy = updateStrategy.xAppNameHeader(name);
        }
        if (login != null) {
            updateStrategy = updateStrategy.xTcsLoginHeader(login);
        }
        updateStrategy.execute(ResponseBodyData::asString);
        assertThat("номера стратегии не равно", updateStrategy.execute(ResponseBodyData::asString).substring(57, 98), is("errorMessage\":\"Сервис временно недоступен"));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482599")
    @DisplayName("C482599.UpdateStrategy. Валидация запроса: strategyId не в формате UUID")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482599() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 102 - Описание";
        String titleUpdate = "Стратегия Autotest 102 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 102 - Обновленное Описание";
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("8f8da33d96c1445880eab27d8e91b976")
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, null, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE);
    }


    @Test
    @AllureId("482609")
    @DisplayName("C482609.UpdateStrategy.Валидация запроса: x-tcs-login > 20 символов")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482609() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 103 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 103 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 103 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_adminasrtbv3")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.USD, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482619")
    @DisplayName("C482619.UpdateStrategy.Валидация запроса: параметр title < 1 символа")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482619() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 104 - Описание";
        Integer score = 1;
        String titleUpdate = "";
        String descriptionUpdate = "Стратегия Autotest 104 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.USD, "active", StrategyRiskProfile.AGGRESSIVE);
    }


    @Test
    @AllureId("482621")
    @DisplayName("C482621.UpdateStrategy.Валидация запроса: параметр title > 50 символов")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482621() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 105 - Описание";
        Integer score = 1;
        String titleUpdate = "Общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        String descriptionUpdate = "Стратегия Autotest 105 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.moderate,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.USD, "active", StrategyRiskProfile.MODERATE);
    }


    @Test
    @AllureId("482635")
    @DisplayName("C482635.UpdateStrategy.Валидация запроса: параметр description < 1 символа")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482635() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 106 - Описание";
        Integer score = 1;
        String titleUpdate = "Общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        String descriptionUpdate = "";
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482649")
    @DisplayName("C482649.UpdateStrategy.Валидация запроса: параметр description > 500 символов")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482649() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 107 - Описание";
        String titleUpdate = "Стратегия Autotest 107 - Обновленый Заголовок";
        String descriptionUpdate = "Страте́гия (др.-греч. — искусство полководца) — общий, недетализированный план," +
            " охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо" +
            " деятельности человека. Задачей стратегии является эффективное использование наличных ресурсов для" +
            " достижения основной цели (стратегия как способ действий становится особо необходимой в ситуации, когда" +
            " для прямого достижения основной цели недостаточно наличных ресурсов).Понятие произошло от понятия военная" +
            " стратегия — наука о ведении войны, одна из областей военного искусства, высшее его проявление, которое" +
            " охватывает вопросы теории и практики подготовки к войне, её планирование и ведение, исследует закономерности войны.";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, null, Currency.RUB, "draft", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482680")
    @DisplayName("C482680.UpdateStrategy.Авторизация: не передан apiKey")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482680() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 108 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 108 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 108 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApi.updateStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(401));
        updateStrategy.execute(ResponseBodyData::asString);
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482690")
    @DisplayName("C482690.UpdateStrategy.Авторизация: передано неверное значение apiKey")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482690() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 109 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 109 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 109 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "trackinaxcg"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482702")
    @DisplayName("C482702.UpdateStrategy. Не переданы атрибуты: title, description, score")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482702() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 110 - Описание";
        Integer score = 1;
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "draft", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482703")
    @DisplayName("C482703.UpdateStrategy. Параметр title = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482703() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 111 - Описание";
        Integer score = 1;
        String titleUpdate = null;
        String descriptionUpdate = "Стратегия Autotest 111 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("482704")
    @DisplayName("C482704.UpdateStrategy. Параметр strategyId не существующее значение")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482704() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 112 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 112 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 112 - Обновленное Описание";
        //Создаем клиента в БД tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("84144088-7caa-44d2-a988-88cf46d28888")
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
    }


    private static Stream<Integer> scoresForUpdateStrategy() {
        return Stream.of(-1, 0, 6);
    }

    @ParameterizedTest
    @MethodSource("scoresForUpdateStrategy")
    @AllureId("839748")
    @DisplayName("C839748.UpdateStrategy. Валидация запроса: значение score вне интервала [1:5]")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839748(Integer scoresForUpdateStrategy) {
        parameter("score", scoresForUpdateStrategy);
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 113 - Описание";
        Integer score = 3;
        String titleUpdate = "Стратегия Autotest 113 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 113 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoresForUpdateStrategy);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("839768")
    @DisplayName("C839768.UpdateStrategy. Проверка ограничений для активированной стратегии, score != null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839768() {
        BigDecimal expectedRelativeYield = new BigDecimal(10.00);
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 114 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 114 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 114 - Обновленное Описание";
        Integer scoreUpdate = null;
        //Создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
    }

    @Test
    @AllureId("1575355")
    @DisplayName("C1575355.UpdateStrategy.Валидация запроса: параметр expectedRelativeYield содержит дробную часть !=0")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1575355() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        BigDecimal expectedRelativeYieldUpdate = new BigDecimal(20.5);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 101 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 101 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 101 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
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
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYieldUpdate);
        //Вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));

        updateStrategy.execute(ResponseBodyData::asString);
        assertThat("ответ метода не равен", updateStrategy.execute(ResponseBodyData::asString).substring(57,157), is("errorMessage\":\"Дробная часть ожидаемого процента доходности должна быть равна нулю или отсутствовать"));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);

    }


    //*** Методы для работы тестов ***
    //************************************************************************************************

    void checkParamDB(UUID strategyId, String contractId, String title, String description, Integer score,
                      Currency currency, String status, StrategyRiskProfile riskProfile) {
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(score));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(currency.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(riskProfile.toString()));
    }
}
