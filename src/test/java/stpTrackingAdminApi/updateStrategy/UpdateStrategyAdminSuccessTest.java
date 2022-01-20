package stpTrackingAdminApi.updateStrategy;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.StrategyTest;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequestOwner;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;
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
    InvestTrackingAutoConfiguration.class

})

public class UpdateStrategyAdminSuccessTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    Strategy strategy;
    String SIEBEL_ID = "4-1UBHYQ63";
    String xApiKey = "x-api-key";
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);

    @Autowired
    ByteArrayReceiverService kafkaReceiver;
//    @Autowired
//    BillingService billingService;
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
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 001 - Описание";
        Integer score = 1;
        String titleUpdate = "New Autotest " +String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield,"TEST", "OwnerTEST");

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
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        checkParamEvent(event, "UPDATED", strategyId, titleUpdate);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("482511")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482511.UpdateStrategy. Успешное обновление стратегии админом, статус 'draft'")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482511() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 002 - Описание";
        String titleUpdate = "New Autotest " +String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 002 - Обновленное Описание";
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        Integer scoreUpdate = 5;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield,"TEST", "OwnerTEST");
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

        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("482513")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482513.UpdateStrategy. Успешное обновление стратегии админом, только description")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482513() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 003 - Описание";
        String descriptionUpdate = "Стратегия Autotest 003 - Обновленное Описание";
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        Integer score = null;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield,"TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");

        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, descriptionUpdate, score, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("482514")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482514.UpdateStrategy. Успешное обновление стратегии админом, только title")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482514() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 004 - Описание";
        String titleUpdate = "New Autotest " +String.valueOf(randomNumber);
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        Integer scoreUpdate = null ;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null, expectedRelativeYield,"TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");


        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, titleUpdate, description, scoreUpdate, Currency.RUB, "draft", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("838783")
    @Subfeature("Успешные сценарии")
    @DisplayName("C838783.UpdateStrategy. Успешное обновление стратегии админом, только score")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C838783() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 005 - Описание";
        Integer score = 1;
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield,"TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");

        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, description, scoreUpdate, Currency.RUB, "active", StrategyRiskProfile.CONSERVATIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("482516")
    @Subfeature("Успешные сценарии")
    @DisplayName("C482516.UpdateStrategy. Успешное обновление title, score,description, expected_relative_yield, short_description, owner_description")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482516() {

        BigDecimal expectedRelativeYield = new BigDecimal(100.00);
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 006 - Описание";
        Integer score = 1;
        String titleUpdate ="New Autotest" +String.valueOf(randomNumber);
        String descriptionUpdate = "New Стратегия Autotest 006 - Описание";;
        Integer scoreUpdate = 5;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), score, expectedRelativeYield,"TEST", "OwnerTEST");
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


        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.USD, "active", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("839319")
    @Subfeature("Успешные сценарии")
    @DisplayName("C839319.UpdateStrategy. Успешное обновление title & description стратегии, score = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839319() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 007 - Описание";
        Integer score = null;
        String titleUpdate = "New Autotest " +String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 007 - Обновленное Описание";
        Integer scoreUpdate = null;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield,"TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest. setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");

        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, titleUpdate, descriptionUpdate, scoreUpdate, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("839399")
    @Subfeature("Успешные сценарии")
    @DisplayName("C839399.UpdateStrategy.Успешное обновление стратегии description & score = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839399() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 008 - Описание";
        Integer score = 1;
        String descriptionUpdate ="New Стратегия Autotest 008 - Описание";
        Integer scoreUpdate = null;
        String shortDescriptionUpdate = "TEST100";
        String ownerDescription = "OwnerTEST100";
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield,"TEST", "OwnerTEST");
        //Формируем body для метода updateStrategy
        UpdateStrategyRequestOwner owner = new UpdateStrategyRequestOwner();
        owner.setDescription("OwnerTEST100");
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        updateStrategyRequest.setOwner(owner);
        updateStrategyRequest.setExpectedRelativeYield(expectedRelativeYield);
        updateStrategyRequest.setShortDescription("TEST100");

        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkParamDB(strategyId, contractId, title, descriptionUpdate, scoreUpdate, Currency.USD, "draft", StrategyRiskProfile.AGGRESSIVE, shortDescriptionUpdate,ownerDescription,expectedRelativeYield);
    }


    @Test
    @AllureId("1363648")
    @DisplayName("C1363648.UpdateStrategy. Обновили стратегию с массивом тестирования из настроек strategy-test-ids")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1363648() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 001 - Описание";
        Integer score = 1;
        String titleUpdate = "New Autotest " +String.valueOf(randomNumber);
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield,"TEST", "OwnerTEST");

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
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
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
        //Проверяем обновление массива tests
        Strategy getDataFromStategy = strategyService.getStrategy(strategyId);
        for (int i = 0; i < tests.size(); i++){
            assertThat("test != " + tests.get(i).getId(), getDataFromStategy.getTestsStrategy().get(i).getId(), is(tests.get(i).getId()));
        }
    }


    @Test
    @AllureId("1363651")
    @DisplayName("C1363651.UpdateStrategy. Обновили стратегию с пустым массивом настроек strategy-test-ids и только с данным массивом")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C1363651() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "Стратегия Autotest 001 - Описание";
        Integer score = 1;
        UUID strategyId = UUID.randomUUID();
        //Находим клиента в БД social
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
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
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield,"TEST", "OwnerTEST");

        List<StrategyTest> tests = new ArrayList<>();

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTests(tests);
        //Вызываем метод updateStrategy
        UpdateStrategyResponse responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(UpdateStrategyResponse.class));
        //Проверяем ответ
        assertThat("test != []", responseUpdateStrategy.getTests().toString(), is("[]"));
        //Проверяем обновление массива tests
        Strategy getDataFromStategy = strategyService.getStrategy(strategyId);
        assertThat("test != []", getDataFromStategy.getTestsStrategy().toString(), is("[]"));
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
    }

    void checkParamDB(UUID strategyId, String contractId, String title, String description, Integer score,
                      Currency currency, String status, StrategyRiskProfile riskProfile, String shortDescriptionUpdate,String ownerDescription,BigDecimal expectedRelativeYield) {
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
    }

    void checkParamEvent(Tracking.Event event, String action, UUID strategyId, String title) {
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID стратегии не равен", uuid(event.getStrategy().getId()), is(strategyId));
        assertThat("название стратегии не равен", (event.getStrategy().getTitle()), is(title));

    }
}