package stpTrackingAdminApi.activateStrategy;


import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.Topics;
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
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufBytesReceiver;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufCustomReceiver;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Slf4j
@Epic("activateStrategy - Активация стратегии")
@Feature("TAP-6815")
@Subfeature("Успешные сценарии")
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaProtobufFactoryAutoConfiguration.class
})
public class ActivateStrategySuccessTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    Client client;
    Contract contract;
    Strategy strategy;
    String SIEBEL_ID = "5-55RUONV5";

    @Resource(name = "customReceiverFactory")
    KafkaProtobufCustomReceiver<String, byte[]> kafkaReceiver;

    @Resource(name = "bytesReceiverFactory")
    KafkaProtobufBytesReceiver<String, BytesValue> receiverBytes;

    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
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
                contractService.deleteContract(contract);
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
    @DisplayName("C457274.ActivateStrategy. Успешная активация стратегии")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C457274() throws Exception {
        String title = "Тест стратегия Autotest 001 - Заголовок";
        String description = "Тест стратегия Autotest 001 - Описание";
        Integer score = 5;
        UUID strategyId = UUID.randomUUID();

        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
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

        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);

        steps.createContractAndStrategy(client, contract, strategy, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);

        //Вычитываем из топика кафка tracking.event все offset
        resetOffsetToLate(TRACKING_EVENT);

        //Вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());

        //Смотрим, сообщение, которое поймали в топике kafka
        Map<String, byte[]> message = await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_EVENT.getName()), is(not(empty()))
            )
            .stream().findFirst().orElseThrow(() -> new RuntimeException("Сообщений не получено"));

        Tracking.Event event = Tracking.Event.parseFrom(message.values().stream().findAny().get());

        //Проверяем, данные в сообщении
        checkEventParam(event, "CREATED", strategyId, title);
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE, score);
    }


    @Test
    @AllureId("457273")
    @DisplayName("C457273.ActivateStrategy. Активируем стратегию с description is null")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457273() {
        String title = "Тест стратегия Autotest 002 - Заголовок";
        String description = null;
        Integer score = 5;
        UUID strategyId = UUID.randomUUID();

        //Находим клиента в БД Social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());

        //Получаем данные по клиенту в API-сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();

        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        steps.createContractAndStrategy(client, contract, strategy, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);

        //Вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());

        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "draft",
            StrategyRiskProfile.CONSERVATIVE, score);
    }


    @Test
    @AllureId("457351")
    @DisplayName("C457351.ActivateStrategy. Успешный ответ при повторной активации")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457351() throws Exception {
        String title = "Тест стратегия Autotest 003 - Заголовок";
        String description = "Тест стратегия Autotest 003 - Описание";
        Integer score = 5;
        UUID strategyId = UUID.randomUUID();
        LocalDateTime dateCreateTr = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        //Находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
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
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        steps.createContractAndStrategy(client, contract, strategy, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score);

        //Вычитываем из топика кафка tracking.event все offset
        resetOffsetToLate(TRACKING_EVENT);

        //Вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());

        //Смотрим, сообщение, которое поймали в топике kafka
        Map<String, byte[]> message = await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_EVENT.getName()), is(not(empty()))
            )
            .stream().findFirst().orElseThrow(() -> new RuntimeException("Сообщений не получено"));

        Tracking.Event event = Tracking.Event.parseFrom(message.values().stream().findAny().get());

        //Проверяем, данные в сообщении
        checkEventParam(event, "CREATED", strategyId, title);

        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE, score);
        strategyApi.activateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.asString());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, StrategyBaseCurrency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE, score);
    }


    //*** Методы для работы тестов ***
    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> receiverBytes.receiveBatch(topic.getName(),
                Duration.ofSeconds(3), BytesValue.class), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }

    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    //Проверяем параметры события
    void checkEventParam(Tracking.Event event, String action, UUID strategyId, String title) {
        assertThat("Action события не равен", event.getAction().toString(), is(action));
        assertThat("ID договора не равен", uuid(event.getStrategy().getId()), is(strategyId));
        assertThat("ID стратегии не равен", (event.getStrategy().getTitle()), is(title));
    }

    //Проверяем параметры стратегии
    void checkStrategyParam(UUID strategyId, String contractId, String title, StrategyBaseCurrency baseCurrency,
                            String description, String status, StrategyRiskProfile riskProfile, Integer score) {
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", strategy.getTitle(), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(score));
        assertThat("валюта стратегии не равно", strategy.getBaseCurrency().toString(), is(baseCurrency.getValue()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", strategy.getRiskProfile().toString(), is(riskProfile.toString()));
    }
}