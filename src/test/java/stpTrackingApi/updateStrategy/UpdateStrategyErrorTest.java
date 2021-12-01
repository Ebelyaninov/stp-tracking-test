package stpTrackingApi.updateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import org.json.JSONObject;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.NullableCurrency;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;


@Epic("updateStrategy - Обновление параметров стратегии ведущим")
@Feature("TAP-6784")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    KafkaAutoConfiguration.class
})
public class UpdateStrategyErrorTest {
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService сontractService;
    @Autowired
    StrategyService strategyService;

    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client client;
    Contract contract;
    Strategy strategy;
    Profile profile;
    String SIEBEL_ID = "5-KHGHC74O";

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
            Arguments.of(null, "android", "4.5.6"),
            Arguments.of("trading-invest", null, "I.3.7"),
            Arguments.of("trading", "ios 8.1", null)
        );
    }


    @ParameterizedTest
    @MethodSource("provideStringsForHeadersUpdateStrategy")
    @AllureId("542528")
    @DisplayName("C542528.UpdateStrategy.Валидация запроса: обязательные входные параметры в headers: X-APP-NAME, X-APP-VERSION, X-APP-PLATFORM")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C542528(String name, String version, String platform) {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        //вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            updateStrategy = updateStrategy.xAppNameHeader(name);
        }
        if (version != null) {
            updateStrategy = updateStrategy.xAppVersionHeader(version);
        }
        if (platform != null) {
            updateStrategy = updateStrategy.xPlatformHeader(platform);
        }
        Response getResponseOfUpdateStrategy =  updateStrategy.execute(response -> response);
        assertThat("номера стратегии не равно", getResponseOfUpdateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542529")
    @DisplayName("C542529.UpdateStrategy.SiebelId = null в заголовке с key = 'X-TCS-SIEBEL-ID")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542529() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Недостаточно прав"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542530")
    @DisplayName("C542530.UpdateStrategy.Не удалось получить clientId ИЛИ clientId = NULL в кеш")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542530() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader("1-10282II")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542531")
    @DisplayName("C542531.UpdateStrategy.Статус стратегии != 'draft'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542531() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе активная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active, LocalDateTime.now());
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542532")
    @DisplayName("C542532.UpdateStrategy.Не найдено значение strategyId, передаваемое в запросе")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542532() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath("6087b7df-0b05-4afa-a55b-8a2eeb457833")
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }



    @Test
    @AllureId("542533")
    @DisplayName("C542533.UpdateStrategy.Не переданы Параметры: title, Description")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542533() {
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
//        request.setTitle(null);
//        request.setDescription(null);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Не заданы параметры для обновления"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("1187384")
    @DisplayName("C1187384.UpdateStrategy.Обновление стратегии значение title > 30 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C1187384() {
        UUID strategyId = UUID.randomUUID();
        String title = "общий, недетализированный план.";
        String description = "Тестовая стратегия для автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Некорректное название для стратегии"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("1187385")
    @DisplayName("C1187385.UpdateStrategy.Обновление стратегии значение title < 1 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C1187385() {
        UUID strategyId = UUID.randomUUID();
        String title = "";
        String description = "Тестовая стратегия для автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542535")
    @DisplayName("C542535.UpdateStrategy.Обновление стратегии значение description > 500 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542535() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 03";
        String description = "Страте́гия (др.-греч. — искусство полководца) — общий, недетализированный план, " +
            "охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо" +
            " деятельности человека. Задачей стратегии является эффективное использование наличных ресурсов для " +
            "достижения основной цели (стратегия как способ действий становится особо необходимой в ситуации," +
            " когда для прямого достижения основной цели недостаточно наличных ресурсов).Понятие произошло от" +
            " понятия военная стратегия — наука о ведении войны, одна из областей военного искусства, высшее его" +
            " проявление, которое охватывает вопросы теории и практики подготовки к войне, её планирование и ведение," +
            " исследует закономерности войны.";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
//        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.NullableStrategyBaseCurrency.USD);
//        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.NullableStrategyRiskProfile.MODERATE);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542536")
    @DisplayName("C542536.UpdateStrategy.SiebelId передан меньше 1 символа")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542536() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
//        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.NullableStrategyBaseCurrency.USD);
//        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.NullableStrategyRiskProfile.MODERATE);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader("")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }

    @Test
    @AllureId("542537")
    @DisplayName("C542537.UpdateStrategy.SiebleId передан более 12 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542537() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //находим клиента в social и берем данные по профайлу
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID + "12345")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }

    @Test
    @AllureId("560148")
    @DisplayName("C560148.UpdateStrategy.На вход передан параметр riskProfile")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C560148() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.NullableStrategyRiskProfile.MODERATE);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Нельзя обновить уровень риска или базовую валюту у созданной стратегии"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }



    @Test
    @AllureId("560149")
    @DisplayName("C560149.UpdateStrategy.На вход передан параметр baseCurrency")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C560149() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.NullableCurrency.USD);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"), is("Нельзя обновить уровень риска или базовую валюту у созданной стратегии"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is("Тестовая стратегия для работы автотестов"));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("1141663")
    @DisplayName("C1141663.Валидация запроса: Получение параметра desctiption IS NULL")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C1141663() {
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(null);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        assertThat("errorCode не равен Error", updateStrategy.getBody().jsonPath().get("errorCode"), equalTo("Error"));
        assertThat("errorMessage не равно Сервис временно недоступен", updateStrategy.getBody().jsonPath().get("errorMessage"), equalTo("Сервис временно недоступен"));
    }


    //***методы для работы тестов**************************************************************************

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategyMulti(UUID investId, ClientStatusType сlientStatusType, SocialProfile socialProfile, String contractId, UUID strategyId, ContractRole contractRole,
                                                  ContractState contractState, StrategyCurrency strategyCurrency,
                                                  StrategyRiskProfile strategyRiskProfile, StrategyStatus strategyStatus, LocalDateTime date) {
        client = clientService.createClient(investId, сlientStatusType, socialProfile, null);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = сontractService.saveContract(contract);
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle("Тест стратегия автотестов")
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription("Тестовая стратегия для работы автотестов")
            .setStatus(strategyStatus)
            .setSlavesCount(0)
            .setActivationTime(date)
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false);

        strategy = trackingService.saveStrategy(strategy);
    }
}
