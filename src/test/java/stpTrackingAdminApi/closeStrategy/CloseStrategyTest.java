package stpTrackingAdminApi.closeStrategy;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;
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
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.StrategyApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioPositionRetentionDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_STRATEGY_EVENT;

@Slf4j
@Epic("closeStrategy - Закрытие стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Subfeature("Успешные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("closeStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    KafkaAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    AdminApiCreatorConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    ApiCreatorConfiguration.class
})
public class CloseStrategyTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
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
    StpTrackingAdminSteps stpTrackingAdminSteps;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    StpSiebel siebel;
    @Autowired

    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioPositionRetentionDao masterPortfolioPositionRetentionDao;

    Strategy strategy;
    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";
    String description = "Autotest  - CloseStrategy";
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);
    String title;
    Profile profile;
    SocialProfile socialProfile;
    UUID investIdMaster;
    String contractIdMaster;
    UUID strategyId;

    @BeforeAll
    void createTestData() {
        title = stpTrackingAdminSteps.getTitleStrategy();
        description = "Стратегия Autotest 001 - Описание";
        //Находим клиента в БД social
        profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        socialProfile = stpTrackingAdminSteps.getProfile(siebel.siebelIdAdmin);
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        stpTrackingAdminSteps.deleteDataFromDb(siebel.siebelIdAdmin);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioPositionRetentionDao.deleteMasterPortfolioPositionRetention(strategyId);
            } catch (Exception e) {
            }
        });
    }

    @Test
    @AllureId("1914673")
    @DisplayName("C1914673.CloseStrategy.Успешное закрытие стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1914673() throws InvalidProtocolBufferException {
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        strategyId = UUID.randomUUID();
        String title = "Autotest 001" + dateNow;
        String description = "New test стратегия Autotest 001 " + dateNow;
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        Response responseCloseStrategy = strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCloseStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCloseStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", event);
        Contract contract = contractService.getContract(contractIdMaster);
        //Проверяем, данные в сообщении
        checkEventParam(event, "UPDATED", strategyId, title, contract.getClientId());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractIdMaster, title, Currency.RUB, description, "closed",
            StrategyRiskProfile.CONSERVATIVE, 3, dateNow);
    }


    @Test
    @AllureId("1915257")
    @DisplayName("C1915257.CloseStrategy.Определяем стратегию: strategy.status = closed")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915257() throws InvalidProtocolBufferException {
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        strategyId = UUID.randomUUID();
        String title = "Autotest 001" + dateNow;
        String description = "New test стратегия Autotest 001 " + dateNow;
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.closed, 0, LocalDateTime.now().minusHours(1), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", LocalDateTime.now());
        Response responseCloseStrategy = strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCloseStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCloseStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractIdMaster, title, Currency.RUB, description, "closed",
            StrategyRiskProfile.CONSERVATIVE, 3, dateNow);
    }


    private static Stream<Arguments> provideStringsForHeadersCloseStrategy() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCloseStrategy")
    @AllureId("1915039")
    @DisplayName("C1915039.CloseStrategy.Валидация основных параметров: x-app-name, x-tcs-login")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C455794(String name, String login) {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //вызываем метод closeStrategy
        StrategyApi.CloseStrategyOper closeStrategy = strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            closeStrategy = closeStrategy.xAppNameHeader(name);
        }
        if (login != null) {
            closeStrategy = closeStrategy.xTcsLoginHeader(login);
        }
        closeStrategy.execute(ResponseBodyData::asString);
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("frozen"));
    }


    @Test
    @AllureId("1915149")
    @DisplayName("C1915149.CloseStrategy.Валидация запроса: значение X-TCS-LOGIN > 20 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915149() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //вызываем метод confirmMasterClient со значением Login > 20 символов
        strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admintracking_admin1232422353456")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("frozen"));

    }

    @Test
    @AllureId("1915086")
    @DisplayName("C1915086.CloseStrategy.Авторизация: не передаем X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915086() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("frozen"));
    }


    @Test
    @AllureId("1915140")
    @DisplayName("C1915140.CloseStrategy.Авторизация: Неверное значение X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915140() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //вызываем метод confirmMasterClient с неверным значением api-key
        strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "trackidngc"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("frozen"));
    }


    @Test
    @AllureId("1915144")
    @DisplayName("C1915144.CloseStrategy.Авторизация: Значение X-API-KEY с доступом read")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915144() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //вызываем метод confirmMasterClient с неверным значением api-key
        strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("frozen"));
    }

    @Test
    @AllureId("1915082")
    @DisplayName("C1915082.CloseStrategy.Определяем стратегию: strategy.status = active")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915082() throws JSONException {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //вызываем метод CloseStrategy
        StrategyApi.CloseStrategyOper closeStrategy = strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422));
        JSONObject jsonObject = new JSONObject(closeStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-20-B01"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Стратегия не найдена"));
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
    }

    @Test
    @AllureId("1915202")
    @DisplayName("C1915202.CloseStrategy.Проверяем, что на стратегии отсутствуют ведомые: strategy.slaves_count > 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для закрытия стратегии (перевод в статус closed).")
    void C1915202() throws JSONException {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 423, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //вызываем метод CloseStrategy
        StrategyApi.CloseStrategyOper closeStrategy = strategyApiStrategyApiAdminCreator.get().closeStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(422));
        JSONObject jsonObject = new JSONObject(closeStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-20-B19"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
        //проверяем, что статус стратегии не изменился
        strategy = strategyService.getStrategy(strategyId);
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("frozen"));
    }



    //Проверяем параметры события
    void checkEventParam(Tracking.Event event, String action, UUID strategyId, String title, UUID clientId) {
        assertAll(
            () -> assertThat("Action события не равен", event.getAction().toString(), is(action)),
            () -> assertThat("ID договора не равен", uuid(event.getStrategy().getId()), is(strategyId)),
            () -> assertThat("ID стратегии не равен", (event.getStrategy().getTitle()), is(title)),
            () -> assertThat("strategy.status записи после обновления != ", event.getStrategy().getStatus().toString(), is(Tracking.Strategy.Status.CLOSED.toString())),
            () -> assertThat("strategy.owner.invest_id записи после обновления != ", uuid(event.getStrategy().getOwner().getInvestId()), is(clientId))
        );
    }

    //Проверяем параметры стратегии
    void checkStrategyParam(UUID strategyId, String contractId, String title, Currency baseCurrency,
                            String description, String status, StrategyRiskProfile riskProfile, Integer score, String time) {
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", strategy.getTitle(), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(score));
        assertThat("валюта стратегии не равно", strategy.getBaseCurrency().toString(), is(baseCurrency.getValue()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", strategy.getRiskProfile().toString(), is(riskProfile.toString()));
        LocalDateTime closeTime =  strategy.getCloseTime();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String closeTimeNow = (fmt.format(closeTime));
        assertThat("дата закрытия стратегии не равно", closeTimeNow, is(time));
    }

    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }


}
