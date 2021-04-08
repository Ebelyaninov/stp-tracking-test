package stpTrackingAdminApi.activateStrategy;


import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
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
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.tracking.steps.StpTrackingAdminSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Slf4j
@Epic("activateStrategy -  Активация стратегии")
@Feature("TAP-6815")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class})
public class ActivateStrategySuccessTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client client;
    Contract contract;
    Strategy strategy;
    private KafkaHelper kafkaHelper = new KafkaHelper();
    String SIEBEL_ID = "5-DMYMSG6P";

    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService сontractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps steps;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                сontractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {
            }
        });
    }

    @Test
    @AllureId("457274")
    @DisplayName("C457274.ActivateStrategy.Успешная активация стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457274() throws Exception {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        steps.createContractAndStrategy(client, contract, strategy, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
        try (KafkaMessageConsumer<byte[], byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.event",
                     ByteArrayDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);
            messageConsumer.startUp();
            Thread.sleep(5000);
            //смотрим, сообщение, которое поймали в топике kafka
            KafkaMessageConsumer.Record<byte[], byte[]> record = messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Сообщение не получено"));
            List<KafkaMessageConsumer.Record<byte[], byte[]>> records = messageConsumer.listRecords();
            for (int i = 0; i < records.size(); i++) {
                Tracking.Event eventBefore = Tracking.Event.parseFrom(records.get(i).value);
                if ("CREATED".equals(eventBefore.getAction().toString())
                    & (strategyId.toString().equals(uuid(eventBefore.getStrategy().getId()).toString()))) {
                    event = eventBefore;
                    break;
                }
            }
//            event = Tracking.Event.parseFrom(record.value);
//            event = Tracking.Event.parseFrom(records.get(records.size()-1).value);
            dateCreateTr = Instant.ofEpochSecond(event.getCreatedAt().getSeconds(), event.getCreatedAt().getNanos())
                .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        }
        //проверяем, данные в сообщении
        checkEventParam(event, "CREATED", strategyId, title);
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStratedyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("457273")
    @DisplayName("C457273.ActivateStrategy.Активируем стратегию с description is null")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии. ")
    void C457273() {
        String title = "тест стратегия autotest";
        String description = null;
        UUID strategyId = UUID.randomUUID();
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        steps.createContractAndStrategy(client, contract, strategy, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStratedyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "draft",
            StrategyRiskProfile.CONSERVATIVE);
    }


    @Test
    @AllureId("457351")
    @DisplayName("C457351.ActivateStrategy.Успешный ответ при повторной активации")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457351() throws Exception {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        steps.createContractAndStrategy(client, contract, strategy, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        //вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем событие в kafka
        try (KafkaMessageConsumer<byte[], byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.event",
                     ByteArrayDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //смотрим, сообщение, которое поймали в топике kafka
            KafkaMessageConsumer.Record<byte[], byte[]> record = messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Сообщение не получено"));
//            Thread.sleep(5000);
            List<KafkaMessageConsumer.Record<byte[], byte[]>> records = messageConsumer.listRecords();
//            event = Tracking.Event.parseFrom(record.value);
            event = Tracking.Event.parseFrom(records.get(records.size() - 1).value);
            dateCreateTr = Instant.ofEpochSecond(event.getCreatedAt().getSeconds(), event.getCreatedAt().getNanos())
                .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        }
//        String dateResponse = responseHeader.substring(0, 19);
//        String dateFromEvent = dateCreateTr.format(formatter);
        //проверяем, данные в сообщении
        checkEventParam(event, "CREATED", strategyId, title);
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStratedyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE);
        strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.asString());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStratedyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE);
    }

    //***методы для работы тестов**************************************************************************

    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    // проверяем параметры события
    void checkEventParam(Tracking.Event event, String action, UUID strategyId, String title) {
        assertThat("Action события не равен", event.getAction().toString(), is(action));
        assertThat("ID договора не равен", uuid(event.getStrategy().getId()), is(strategyId));
        assertThat("ID стратегии не равен", (event.getStrategy().getTitle()), is(title));
    }
    // проверяем параметры стратегии
    void checkStratedyParam(UUID strategyId, String contractId, String title, StrategyBaseCurrency baseCurrency,
                            String description, String status, StrategyRiskProfile riskProfile) {
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(baseCurrency.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(riskProfile.toString()));
    }


}
