package stpTrackingApi.updateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.LocalDateTime;
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
@Tags({@Tag("stp-tracking-api"), @Tag("updateStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
})
public class UpdateStrategyErrorTest {
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    @Autowired
    StpTrackingApiSteps steps;


    Strategy strategy;
    String SIEBEL_ID;
    String title = "Тест стратегия автотестов";
    String description = "Autotest  - UpdateStrategy";
    UUID investId;
    String contractId;

    @BeforeAll
    void getData() {
        SIEBEL_ID = stpSiebel.siebelIdApiMaster;
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(SIEBEL_ID);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(steps.strategyMaster);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientMaster);
            } catch (Exception e) {
            }
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
//        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, null, contractId, strategyId, null, ContractState.untracked,
//            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);

        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");

        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        //вызываем метод updateStrategy
        StrategyApi.UpdateStrategyOper updateStrategy = strategyApiCreator.get().updateStrategy()
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
        Response getResponseOfUpdateStrategy = updateStrategy.execute(response -> response);
        assertThat("номера стратегии не равно", getResponseOfUpdateStrategy.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе активная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "общий, недетализированный план.";
        String descriptionNew = "Тестовая стратегия для автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        assertThat("номера стратегии не равно", updateStrategy.getBody().jsonPath().get("errorMessage"),
            is("Некорректное название для стратегии"));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(),
            is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("1187385")
    @DisplayName("C1187385.UpdateStrategy.Обновление стратегии значение title < 1 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C1187385() {
        UUID strategyId = UUID.randomUUID();
        String titleNew = "";
        String descriptionNew = "Тестовая стратегия для автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 03";
        String descriptionNew = "Страте́гия (др.-греч. — искусство полководца) — общий, недетализированный план, " +
            "охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо" +
            " деятельности человека. Задачей стратегии является эффективное использование наличных ресурсов для " +
            "достижения основной цели (стратегия как способ действий становится особо необходимой в ситуации," +
            " когда для прямого достижения основной цели недостаточно наличных ресурсов).Понятие произошло от" +
            " понятия военная стратегия — наука о ведении войны, одна из областей военного искусства, высшее его" +
            " проявление, которое охватывает вопросы теории и практики подготовки к войне, её планирование и ведение," +
            " исследует закономерности войны.";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.NullableStrategyRiskProfile.MODERATE);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        String descriptionNew = "Тестовая стратегия для работы автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.NullableCurrency.USD);
        request.setDescription(descriptionNew);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
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
        String titleNew = "Тест стратегия автотестов 01";
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(titleNew);
        request.setDescription(null);
        // вызываем метод updateStrategy()
        Response updateStrategy = strategyApiCreator.get().updateStrategy()
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


}
