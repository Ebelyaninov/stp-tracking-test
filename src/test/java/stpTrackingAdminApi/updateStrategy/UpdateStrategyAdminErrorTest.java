package stpTrackingAdminApi.updateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequest;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("updateStrategy - Обновление стратегии администратором")
@Feature("TAP-7225")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class})
public class UpdateStrategyAdminErrorTest {
    StrategyApi strategyApi =  ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client client;
    Contract contract;
    Strategy strategy;

    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ContractService сontractService;
    @Autowired
    ClientService clientService;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {}
            try {
                сontractService.deleteContract(contract);
            } catch (Exception e) {}
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {}
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
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482567(String name, String login) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 001";
        String descriptionUpdate = "new test стратегия autotest 001";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
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
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482599")
    @DisplayName("C482599.UpdateStrategy.Валидация запроса: strategyId не в формате UUID")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482599() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 006";
        String descriptionUpdate = "new test стратегия autotest 006";
        UUID strategyId = UUID.randomUUID();
        createClientWithContractStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("8f8da33d96c1445880eab27d8e91b976")
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("482609")
    @DisplayName("C482609.UpdateStrategy.Валидация запроса: x-tcs-login > 20 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482609() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 007";
        String descriptionUpdate = "new test стратегия autotest 007";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_adminasrtbv3")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482619")
    @DisplayName("C482619.UpdateStrategy.Валидация запроса: параметр title < 1 символа")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482619() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "";
        String descriptionUpdate = "new test стратегия autotest 008";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("482621")
    @DisplayName("C482621.UpdateStrategy.Валидация запроса: параметр title > 50 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482621() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        String descriptionUpdate = "new test стратегия autotest 009";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.moderate,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.MODERATE.toString()));
    }


    @Test
    @AllureId("482635")
    @DisplayName("C482635.UpdateStrategy.Валидация запроса: параметр description < 1 символа")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482635() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        String descriptionUpdate = "";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482649")
    @DisplayName("C482649.UpdateStrategy.Валидация запроса: параметр description > 500 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482649() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 010";
        String descriptionUpdate = "Страте́гия (др.-греч. — искусство полководца) — общий, недетализированный план," +
            " охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо" +
            " деятельности человека. Задачей стратегии является эффективное использование наличных ресурсов для" +
            " достижения основной цели (стратегия как способ действий становится особо необходимой в ситуации, когда" +
            " для прямого достижения основной цели недостаточно наличных ресурсов).Понятие произошло от понятия военная" +
            " стратегия — наука о ведении войны, одна из областей военного искусства, высшее его проявление, которое " +
            "охватывает вопросы теории и практики подготовки к войне, её планирование и ведение, исследует закономерности войны.";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482680")
    @DisplayName("C482680.UpdateStrategy.Авторизация: не передан apiKey")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482680() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 011";
        String descriptionUpdate = "new test стратегия autotest 011";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApi.updateStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(401));
        updateStrategy.execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482690")
    @DisplayName("C482690.UpdateStrategy.Авторизация: передано неверное значение apiKey")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482690() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 011";
        String descriptionUpdate = "new test стратегия autotest 011";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "trackinaxcg"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482702")
    @DisplayName("C482702.UpdateStrategy.Не переданы атрибуты: title, description")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482702() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482703")
    @DisplayName("C482703.UpdateStrategy.Параметр title = null")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482703() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = null;
        String descriptionUpdate = "new test стратегия autotest 011";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482704")
    @DisplayName("C482704.UpdateStrategy.Параметр strategyId не существующие значение")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482704() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 012";
        String descriptionUpdate = "new test стратегия autotest 012";
        //создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        createClientWithContractAndStrategy(null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
        Response expectedResponse = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("84144088-7caa-44d2-a988-88cf46d28888")
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
    }


/////////***методы для работы тестов**************************************************************************


    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(ContractRole contractRole, ContractState contractState, UUID strategyId, String title, String description,
                                             StrategyCurrency strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        String SIEBEL_ID = "5-SG3XVXLB";
////        //находим данные клиента в сервисе счетов: договор БС, investId
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();

        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();

        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = сontractService.saveContract(contract);
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);

        strategy = trackingService.saveStrategy(strategy);
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractStrategy(ContractRole contractRole, ContractState contractState, UUID strategyId, String title, String description,
                                          StrategyCurrency strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                          StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        String SIEBEL_ID = "5-SG3XVXLB";
//        //находим данные клиента в сервисе счетов: договор БС, investId
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();

        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();


        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = сontractService.saveContract(contract);
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategy = trackingService.saveStrategy(strategy);
    }
}
