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
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("updateStrategy - Обновление параметров стратегии ведущим")
@Feature("TAP-6784")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("updateStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
})
public class UpdateStrategySuccessTest {
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

    @Test
    @AllureId("542525")
    @DisplayName("C542525.UpdateStrategy.Успешное обновление стратегии, все параметры")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542525() {
        //получаем текущую дату и время
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
        Response exerep = strategyApiCreator.get().updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertFalse(exerep.getHeaders().getValue("x-trace-id").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleNew));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionNew));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542526")
    @DisplayName("C542526.GetUntrackedContracts.Успешное обновление стратегии, параметры, которые НЕ переданы в запросе, оставляем без изменений")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542526() {
        //получаем текущую дату и время
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
        // вызываем метод updateStrategy()
        Response exerep = strategyApiCreator.get().updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertFalse(exerep.getHeaders().getValue("x-trace-id").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleNew));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542527")
    @DisplayName("C542527.UpdateStrategy.Успешное обновление стратегии, для title удаляем все пробелы в начале и в конце значения")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542527() {
        //получаем текущую дату и время
        UUID strategyId = UUID.randomUUID();
        String titleNew = "  Тест стратегия 01    ";
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
        Response exerep = strategyApiCreator.get().updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertFalse(exerep.getHeaders().getValue("x-trace-id").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия 01"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(Currency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionNew));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }

}
