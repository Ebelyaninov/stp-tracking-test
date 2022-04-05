package stpTrackingAdminApi.updateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.StrategyApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.StrategyTest;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequestOwner;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.parameter;
import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_STRATEGY_EVENT;

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
    StpTrackingSiebelConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})

public class UpdateStrategyAdminErrorTest {
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
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;

    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";
    Strategy strategy;

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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.moderate,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        strategyApiStrategyApiAdminCreator.get().updateStrategy()
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
    @AllureId("1705965")
    @DisplayName("C1705965.UpdateStrategy.Авторизация: передано неверное значение apiKey")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1705965() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 109 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 109 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 109 - Обновленное Описание";
        //Создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoresForUpdateStrategy);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
//        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
//            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);

        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        Response expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        assertThat("Отправили событие, если не обновили стратегию", messages.size(), is(0));
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
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYieldUpdate);
        //Вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400));

        updateStrategy.execute(ResponseBodyData::asString);
        assertThat("ответ метода не равен", updateStrategy.execute(ResponseBodyData::asString).substring(57, 157), is("errorMessage\":\"Дробная часть ожидаемого процента доходности должна быть равна нулю или отсутствовать"));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);

    }


    @Test
    @AllureId("1363710")
    @DisplayName("C1363710.UpdateStrategy. Один из элементов массива tests не входит в список из настройки strategy-test-ids")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1363710() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Стратегия Autotest 114 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 114 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 114 - Обновленное Описание";
        Integer scoreUpdate = null;
        //Создаем клиента в tracking: client, contract, strategy
        UUID strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        List<StrategyTest> tests = new ArrayList<>();
        tests.add(new StrategyTest().id("derivative"));
        tests.add(new StrategyTest().id("structured_bonds"));
        tests.add(new StrategyTest().id("closed_fund2"));
        tests.add(new StrategyTest().id("bond"));
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setTests(tests);
        //Вызываем метод updateStrategy
        ErrorResponse expectedResponse = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.as(ErrorResponse.class));
        assertThat("номера стратегии не равно", expectedResponse.getErrorMessage(), is("Указано недопустимое тестирование"));
        assertThat("номера стратегии не равно", expectedResponse.getErrorCode(), is("0344-03-V05"));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, score, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE);
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
