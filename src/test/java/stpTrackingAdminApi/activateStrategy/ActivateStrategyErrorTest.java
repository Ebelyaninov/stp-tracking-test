package stpTrackingAdminApi.activateStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Step;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.StrategyApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_STRATEGY_EVENT;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("activateStrategy -  Активация стратегии")
@Subfeature("Альтернативные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("activateStrategy")})
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
public class ActivateStrategyErrorTest {
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
    StpTrackingAdminSteps steps;
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;


    String description = "Autotest  - ActivateStrategy";
    Integer score = 1;
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);
    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";
    String contractId;
    UUID investId;
    String siebelId;
    MasterPortfolioValue masterPortfolioValue;
    UUID strategyId;

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
            try {
                masterPortfolioValueDao.deleteMasterPortfolioValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractId, strategyId);
            } catch (Exception e) {
            }
        });
    }


    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту master в api сервиса счетов
        siebelId = siebel.siebelIdAdmin;
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdAdmin);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelId);
    }


    @Test
    @AllureId("C457266")
    @DisplayName("C457266.ActivateStrategy. Не переданы обязательные параметры")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C457266() {
        strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем ActiveStrategy
        Response responseActiveStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        Response responseActiveStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
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
        UUID strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft

        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04",null);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем метод activateStrategy с некоррентным значением api-key
        strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "trackinnng"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }


    @Test
    @AllureId("1705386")
    @DisplayName("C1705386.ActivateStrategy. Авторизация по api-key, передаем значение с доступом read")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C1705386() {
        UUID strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем метод activateStrategy с некоррентным значением api-key
        strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
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
        UUID strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем метод activateStrategy без api-key
        strategyApiStrategyApiAdminCreator.get().activateStrategy()
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
        Response responseActiveStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath("85d42f98-cd5e-4bb6-82be-d46e722b8888")
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = responseActiveStrategy.as(ErrorResponse.class);
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-01-B01"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Стратегия не найдена"));
    }


    @Test
    @AllureId("840992")
    @DisplayName("C840992.ActivateStrategy. Проверка ограниченйи параметров при Активация стретегии, score = null")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C840992() {
        Integer score = null;
        UUID strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft, при этом score передаем как null
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04",null);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем метод activateStrategy без api-key
        strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("1253905")
    @DisplayName("C1253905.ActivateStrategy.Нет записи MasterPortfolio в базе Cassandra")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C1253905() {
        UUID strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04",null);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем метод activateStrategy без api-key
        strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
    }


    @Test
    @AllureId("1771464")
    @DisplayName("C1771464.ActivateStrategy.Нет записи таблице master_portfolio_value в базе Cassandra")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C1771464() throws Exception {
        String title = steps.getTitleStrategy();
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04",null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        steps.createMasterPortfolio(contractId, strategyId, 1, "6551.10", masterPos);
        //Вызываем метод activateStrategy
        StrategyApi.ActivateStrategyOper activateStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422));
        activateStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(activateStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-01-B17"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Не найдена запись о стоимости master портфеля в бд"));
    }


    @Test
    @AllureId("1771507")
    @DisplayName("C1771507.ActivateStrategy.Не указано значение minimum_value в таблице master_portfolio_value в базе Cassandra")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C1771507() throws Exception {
        String title = steps.getTitleStrategy();
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        steps.createMasterPortfolio(contractId, strategyId, 1, "6551.10", masterPos);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, null, "6551.10");
        //Вызываем метод activateStrategy
        StrategyApi.ActivateStrategyOper activateStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422));
        activateStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(activateStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-01-B15"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Не найдена минимальная сумма стратегии"));
    }

    @Test
    @AllureId("C1807541")
    @DisplayName("C1807541.ActivateStrategy.Не указано значение value в таблице master_portfolio_value в базе Cassandra")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C1807541() throws Exception {
        String title = steps.getTitleStrategy();
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        steps.createMasterPortfolio(contractId, strategyId, 1, "6551.10", masterPos);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "100", null);
        //Вызываем метод activateStrategy
        StrategyApi.ActivateStrategyOper activateStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422));
        activateStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(activateStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-01-B16"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Не найдена стоимость master портфеля"));
    }


    private static Stream<Arguments> strategyNotFoundStatus () {
        return Stream.of(
            Arguments.of(StrategyStatus.frozen, null),
            Arguments.of(StrategyStatus.closed, LocalDateTime.now())
        );
    }


    @ParameterizedTest
    @MethodSource("strategyNotFoundStatus")
    @SneakyThrows
    @AllureId("1891413")
    @DisplayName("C1891413.ActivateStrategy. Активация стратегии без выполнения условий наличия стратегии или статусe frozen / closed")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C1891413(StrategyStatus strategyStatus,  LocalDateTime dateClose) {
        String title = steps.getTitleStrategy();
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            strategyStatus, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", dateClose);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        steps.createMasterPortfolio(contractId, strategyId, 1, "6551.10", masterPos);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        //Вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = responseActiveStrategy.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-01-B18"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Не найдена стратегия в strategy или не подходит под условия"));
        Strategy strategy = strategyService.findStrategyByContractId(contractId).get();
        assertThat("Обновили статус стратегии", strategy.getStatus(), is(strategyStatus));
    }





//дополнительные методы методы для работы тестов***************************************************
    @Step("Создаем запись в master_portfolio_value: ")
    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String minimumValue, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .minimumValue(minimumValue == null ? null : new BigDecimal(minimumValue))
            .value(value == null ? null : new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }
}
