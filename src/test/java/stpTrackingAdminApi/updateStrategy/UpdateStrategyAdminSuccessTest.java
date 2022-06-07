package stpTrackingAdminApi.updateStrategy;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import jnr.ffi.annotations.In;
import lombok.SneakyThrows;
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
import ru.qa.tinkoff.social.entities.Profile;
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
import ru.qa.tinkoff.swagger.tracking_admin.model.*;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.getLifecycle;
import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_STRATEGY_EVENT;

@Slf4j
@Epic("updateStrategy - Обновление стратегии администратором")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Subfeature("Успешные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("updateStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})

public class UpdateStrategyAdminSuccessTest {
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
    StpTrackingAdminSteps steps;
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    UtilsTest utilsTest = new UtilsTest();

    Strategy strategy;
    String xApiKey = "x-api-key";
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);
    String title;
    String description;
    Profile profile;
    SocialProfile socialProfile;
    UUID investId;
    String contractId;


    @BeforeAll
    void createTestData() {
        title = steps.getTitleStrategy();
        description = "Стратегия Autotest 001 - Описание";
        //Находим клиента в БД social
        profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebel.siebelIdAdmin);
    }

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

    @Test
    @AllureId("482510")
    @DisplayName("C482510.UpdateStrategy. Успешное обновление стратегии Админом, статус 'active'")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482510() throws InvalidProtocolBufferException {
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = 1;
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");

        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
//        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "active", titleUpdate, "rub", "conservative",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @SneakyThrows
    @AllureId("482511")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482511.UpdateStrategy. Успешное обновление стратегии админом, статус 'draft'")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482511() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 002 - Обновленное Описание";
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        Integer scoreUpdate = 5;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        //Получаем данные по клиенту в API-Сервиса счетов
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", titleUpdate, "usd", "aggressive",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @SneakyThrows
    @AllureId("482513")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482513.UpdateStrategy. Успешное обновление стратегии админом, только description")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482513() {
        String descriptionUpdate = "Стратегия Autotest 003 - Обновленное Описание";
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        Integer score = null;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        SocialProfile socialProfile = steps.getProfile(siebel.siebelIdAdmin);
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", title, "usd", "aggressive",
            descriptionUpdate, null, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, strategy.getTitle(), strategy);
        checkParamDB(strategyId, contractId, title, descriptionUpdate, score, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @SneakyThrows
    @AllureId("482514")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482514.UpdateStrategy. Успешное обновление стратегии админом, только title")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482514() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String description = "Стратегия Autotest 004 - Описание";
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        Integer scoreUpdate = null;
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", titleUpdate, "rub", "conservative",
            description, null, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, titleUpdate, description, scoreUpdate, Currency.RUB, "draft", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @AllureId("838783")
    @SneakyThrows
    @Subfeature("Успешные сценарии")
    @DisplayName("C838783.UpdateStrategy. Успешное обновление стратегии админом, только score")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C838783() {
        Integer score = 1;
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "active", title, "rub", "conservative",
            description, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, strategy.getTitle(), strategy);
        checkParamDB(strategyId, contractId, title, description, scoreUpdate, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @AllureId("482516")
    @SneakyThrows
    @Subfeature("Успешные сценарии")
    @DisplayName("C482516.UpdateStrategy. Успешное обновление title, score,description, expected_relative_yield, short_description, owner_description")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482516() {
        BigDecimal expectedRelativeYield = new BigDecimal(100.00);
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = 1;
        String titleUpdate = "New Autotest" + String.valueOf(randomNumber);
        String descriptionUpdate = "New Стратегия Autotest 006 - Описание";
        ;
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "active", titleUpdate, "usd", "aggressive",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.USD, "active", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @AllureId("839319")
    @SneakyThrows
    @Subfeature("Успешные сценарии")
    @DisplayName("C839319.UpdateStrategy. Успешное обновление title & description стратегии, score = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839319() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = null;
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 007 - Обновленное Описание";
        Integer scoreUpdate = null;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", titleUpdate, "usd", "aggressive",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @AllureId("839399")
    @SneakyThrows
    @Subfeature("Успешные сценарии")
    @DisplayName("C839399.UpdateStrategy.Успешное обновление стратегии description & score = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839399() {
        Integer score = 1;
        String descriptionUpdate = "New Стратегия Autotest 008 - Описание";
        Integer scoreUpdate = null;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", title, "usd", "aggressive",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, strategy.getTitle(), strategy);
        checkParamDB(strategyId, contractId, title, descriptionUpdate, scoreUpdate, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
    }


    @Test
    @SneakyThrows
    @AllureId("1363648")
    @DisplayName("C1363648.UpdateStrategy. Обновили стратегию с массивом тестирования из настроек strategy-test-ids")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1363648() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = 1;
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        List<StrategyTest> tests = new ArrayList<>();
        tests.add(new StrategyTest().id("derivative"));
        tests.add(new StrategyTest().id("structured_bonds"));
        tests.add(new StrategyTest().id("closed_fund"));
        tests.add(new StrategyTest().id("bond"));
        tests.add(new StrategyTest().id("structured_income_bonds"));
        tests.add(new StrategyTest().id("foreign_shares"));
        tests.add(new StrategyTest().id("foreign_etf"));
        tests.add(new StrategyTest().id("foreign_bond"));
        tests.add(new StrategyTest().id("russian_shares"));
        tests.add(new StrategyTest().id("leverage"));
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setTests(tests);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", titleUpdate, "rub", "conservative",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        List<Pair<String, byte[]>> filteredMessages = messages.stream()
            .filter(key -> key.getKey().equals(strategyId)).collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        Strategy getDataFromStategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, getDataFromStategy);
        for (int i = 0; i < tests.size(); i++) {
            assertThat("test != " + tests.get(i).getId(), getDataFromStategy.getTestsStrategy().get(i).getId(), is(tests.get(i).getId()));
        }
    }


    @Test
    @SneakyThrows
    @AllureId("1363651")
    @DisplayName("C1363651.UpdateStrategy. Обновили стратегию с пустым массивом настроек strategy-test-ids и только с данным массивом")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1363651() {
        Integer score = 1;
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        List<StrategyTest> tests = new ArrayList<>();
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTests(tests);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем ответ
        assertThat("test != []", responseUpdateStrategy.getTests().toString(), is("[]"));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        List<Pair<String, byte[]>> test = messages.stream()
            .filter(key -> key.getKey().equals(strategyId.toString())).collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        Strategy getDataFromStategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, getDataFromStategy.getTitle(), getDataFromStategy);
        //Проверяем обновление массива tests
        assertThat("test != []", getDataFromStategy.getTestsStrategy().toString(), is("[]"));
    }

    private static Stream<Arguments> provideStringsForSubscriptionStatus() {
        return Stream.of(
            Arguments.of(true, true, false, false),
            Arguments.of(false, false, true, true),
            Arguments.of(false, true, true, false),
            Arguments.of(true, false, false, true)
        );
    }


    @ParameterizedTest
    @MethodSource("provideStringsForSubscriptionStatus")
    @AllureId("C482510")
    @DisplayName("C482510.UpdateStrategy. Успешное обновление стратегии Админом, статус 'active'")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1575248(Boolean buyEnabledForDb, Boolean sellEnabledForDb, Boolean buyEnabled, Boolean sellEnabled) {
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = 1;
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
//        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
//            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield,"TEST", "OwnerTEST", buyEnabledForDb, sellEnabledForDb);

        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", buyEnabledForDb, sellEnabledForDb, false, "0.2", "0.04");


        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");
        updateStrategyRequest.setBuyEnabled(buyEnabled);
        updateStrategyRequest.setSellEnabled(sellEnabled);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "active", titleUpdate, "rub", "conservative",
            descriptionUpdate, scoreUpdate, profile);
        assertThat("buyEnabled не равно " + buyEnabled, responseUpdateStrategy.getBuyEnabled(), is(buyEnabled));
        assertThat("sellEnabled не равно " + sellEnabled, responseUpdateStrategy.getSellEnabled(), is(sellEnabled));
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate, ownerDescription, expectedRelativeYield);
        assertThat("buyEnabled не равно " + buyEnabled, strategy.getBuyEnabled(), is(buyEnabled));
        assertThat("sellEnabled не равно " + sellEnabled, strategy.getSellEnabled(), is(sellEnabled));
    }


    //Проверка обновление null, для парметров expected_relative_yield \ short_description \ owner_description \ score
    //FeeRate пока не исправили
    private static Stream<Arguments> nullValueFor () {
        return Stream.of(
            Arguments.of(null, "shortDescriptionUpdate", "ownerDescription", 1),
            Arguments.of(new BigDecimal(10.00), null, "ownerDescription", 2),
            Arguments.of(new BigDecimal(15.00), "shortDescriptionUpdate", null, 3),
            Arguments.of(new BigDecimal(20.00), "shortDescriptionUpdate", "ownerDescription", null)
        );
    }

    @ParameterizedTest
    @MethodSource("nullValueFor")
    @AllureId("1829154")
    @DisplayName("C1829154.UpdateStrategy. Обновление параметров значением null")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1829154 (BigDecimal expectedRelativeYield, String shortDescription, String ownerDescription, Integer scoreUpdated) {
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = 1;
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        Strategy strategyBeforeUpdate = strategyService.getStrategy(strategyId);
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner updateStrategyRequestOwner = new UpdateStrategyRequestOwner();
        updateStrategyRequestOwner.setDescription(ownerDescription);
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setShortDescription(shortDescription);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setOwner(updateStrategyRequestOwner);
        updateStrategyRequest.setScore(scoreUpdated);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "draft", strategyBeforeUpdate.getTitle(), "rub", "conservative",
            description, scoreUpdated, profile);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, strategyBeforeUpdate.getTitle(), description, scoreUpdated, Currency.RUB, "draft", StrategyRiskProfile.CONSERVATIVE, shortDescription, ownerDescription, expectedRelativeYield);
    }

    @Test
    @AllureId("1890120")
    @DisplayName("C1890120.UpdateStrategy. Апдейт стратегии с active на frozen и снова active")
    @SneakyThrows
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1890120() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        Integer score = 1;
        String titleUpdate = "New Autotest " + String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
        UUID strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investId, null, contractId, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setStatus(UpdateStrategyRequest.StatusEnum.FROZEN);
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);

        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = updateStrategy(updateStrategyRequest, strategyId);
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        checkParamResponse(responseUpdateStrategy, strategyId, "frozen", titleUpdate, "rub", "conservative",
            descriptionUpdate, scoreUpdate, profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.RUB, StrategyStatus.frozen.toString(), StrategyRiskProfile.CONSERVATIVE, strategy.getShortDescription(), strategy.getOwnerDescription(), expectedRelativeYield);

        //Формируем новый body
        UpdateStrategyRequest updateStrategyRequestNew = new UpdateStrategyRequest();
        updateStrategyRequestNew.setStatus(UpdateStrategyRequest.StatusEnum.ACTIVE);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategyNew = updateStrategy(updateStrategyRequestNew, strategyId);
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(200)).until(() ->
            strategyService.getStrategy(strategyId).getStatus(), is(StrategyStatus.active));
        strategy = strategyService.getStrategy(strategyId);
        checkParamResponse(responseUpdateStrategyNew, strategyId, "active", strategy.getTitle(), "rub", "conservative",
            strategy.getDescription(), strategy.getScore(), profile);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messagesFromNewRequest = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> messageFromNewRequset = messagesFromNewRequest.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        event = Tracking.Event.parseFrom(messageFromNewRequset.getValue());
        log.info("Команда в tracking.event:  {}", event);
        //Находим в БД автоследования стратегию и проверяем ее поля);
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate, strategy);
        checkParamDB(strategyId, contractId, strategy.getTitle(), strategy.getDescription(), strategy.getScore(), Currency.RUB, StrategyStatus.active.toString(), StrategyRiskProfile.CONSERVATIVE, strategy.getShortDescription(), strategy.getOwnerDescription(), expectedRelativeYield);
    }


    //*** Методы для работы тестов ***
//*****************************************************************************************************


    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    void checkParamResponse(UpdateStrategyResponse responseUpdateStrategy, UUID strategyId, String status, String title,
                            String currency, String riskProfile, String description, Integer score, Profile profile) {
        //Проверяем, данные которые вернулись в responseUpdateStrategy
        assertThat("номера стратегии не равно", responseUpdateStrategy.getId(), is(strategyId));
        assertThat("status стратегии не равно", responseUpdateStrategy.getStatus().getValue(), is(status));
        assertThat("title стратегии не равно", responseUpdateStrategy.getTitle(), is(title));
        assertThat("baseCurrency стратегии не равно", responseUpdateStrategy.getBaseCurrency().getValue(), is(currency));
        assertThat("riskProfile стратегии не равно", responseUpdateStrategy.getRiskProfile().getValue(), is(riskProfile));
        assertThat("description стратегии не равно", responseUpdateStrategy.getDescription(), is(description));
        assertThat("score стратегии не равно", responseUpdateStrategy.getScore(), is(score));
        assertThat("owner стратегии не равно", responseUpdateStrategy.getOwner().getSocialProfile().getNickname(), is(profile.getNickname()));
        assertThat("slavesCount не равен", responseUpdateStrategy.getSlavesCount(), is(0));
    }

    void checkParamDB(UUID strategyId, String contractId, String title, String description, Integer score,
                      Currency currency, String status, StrategyRiskProfile riskProfile, String shortDescriptionUpdate, String ownerDescription, BigDecimal expectedRelativeYield) {
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(score));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(currency.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(riskProfile.toString()));
        assertThat("expected_relative_yield не равно", strategy.getExpectedRelativeYield(), is(expectedRelativeYield));
        assertThat("short_description не равно", strategy.getShortDescription(), is(shortDescriptionUpdate));
        assertThat("ownerDescription не равно", strategy.getOwnerDescription(), is(ownerDescription));
        assertThat("slavesCount не равен", strategy.getSlavesCount(), is(0));
    }

    void checkParamEvent(Tracking.Event event, String action, UUID strategyId, String title, Strategy strategy) {
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID стратегии не равен", uuid(event.getStrategy().getId()), is(strategyId));
        assertThat("название стратегии не равен", (event.getStrategy().getTitle()), is(title));
        assertThat("status не равен strategy.status записи после обновления", event.getStrategy().getStatus().toString().toLowerCase(), is(strategy.getStatus().toString()));
    }

    UpdateStrategyResponse updateStrategy (UpdateStrategyRequest updateStrategyRequest, UUID strategyId) {
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategyNew = strategyApiStrategyApiAdminCreator.get().updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        return responseUpdateStrategyNew;
    }
}