package stpTrackingAdminApi.updateStrategy;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.InvalidProtocolBufferException;
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
import org.springframework.stereotype.Service;
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
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyRequest;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufBytesReceiver;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufCustomReceiver;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})

@Slf4j
@Epic("updateStrategy - Обновление стратегии администратором")
@Feature("TAP-7225")
@Subfeature("Успешные сценарии")
@Service("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaProtobufFactoryAutoConfiguration.class
})

public class UpdateStrategyAdminSuccessTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    Client client;
    Contract contract;
    Strategy strategy;
    String SIEBEL_ID = "4-1UBHYQ63";

    @Resource(name = "customReceiverFactory")
    KafkaProtobufCustomReceiver<String, byte[]> kafkaReceiver;

    @Resource(name = "bytesReceiverFactory")
    KafkaProtobufBytesReceiver<String, BytesValue> receiverBytes;

    @Autowired
    BillingService billingService;
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

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategy);
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
    @AllureId("482510")
    @DisplayName("C482510.UpdateStrategy. Успешное обновление стратегии Админом, статус 'active'")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482510() throws InvalidProtocolBufferException {
        String title = "Стратегия Autotest 001 - Заголовок";
        String description = "Стратегия Autotest 001 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 001 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 001 - Обновленное Описание";
        Integer scoreUpdate = 5;
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

        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);

        //Вычитываем из топика кафка tracking.event все offset
        resetOffsetToLate(TRACKING_EVENT);

        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());

        //Смотрим, сообщение, которое поймали в топике kafka
        Map<String, byte[]> message = await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_EVENT.getName()), is(not(empty()))
            )
            .stream().findFirst().orElseThrow(() -> new RuntimeException("Сообщений не получено"));

        Tracking.Event event = Tracking.Event.parseFrom(message.values().stream().findAny().get());
        //Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());

        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID стратегии не равен", uuid(event.getStrategy().getId()), is(strategyId));
        assertThat("название стратегии не равен", (event.getStrategy().getTitle()), is(titleUpdate));
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(scoreUpdate));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482511")
    @DisplayName("C482511.UpdateStrategy. Успешное обновление стратегии админом, статус 'draft'")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482511() {
        String title = "Стратегия Autotest 002 - Заголовок";
        String description = "Стратегия Autotest 002 - Описание";
        String titleUpdate = "тратегия Autotest 002 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 002 - Обновленное Описание";
        Integer scoreUpdate = 5;
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

        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());

        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равны", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равны", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равны", (strategy.getTitle()), is(titleUpdate));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(scoreUpdate));
        assertThat("валюта стратегии не равны", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("482513")
    @DisplayName("C482513.UpdateStrategy. Успешное обновление стратегии админом, только description")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482513() {
        String title = "Стратегия Autotest 003 - Заголовок";
        String description = "Стратегия Autotest 003 - Описание";
        String descriptionUpdate = "Стратегия Autotest 003 - Обновленное Описание";
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

        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, null);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(nullValue()));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("482514")
    @DisplayName("C482514.UpdateStrategy. Успешное обновление стратегии админом, только title")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482514() {
        String title = "Стратегия Autotest 004 - Заголовок";
        String description = "Стратегия Autotest 004 - Описание";
        String titleUpdate = "тратегия Autotest 004 - Обновленный Заголовок";
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

        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null);
        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равна", (strategy.getScore()), is(nullValue()));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("838783")
    @DisplayName("C838783.UpdateStrategy. Успешное обновление стратегии админом, только score")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C838783() {
        String title = "Стратегия Autotest 005 - Заголовок";
        String description = "Стратегия Autotest 005 - Описание";
        Integer score = 1;
        Integer scoreUpdate = 5;
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

        //Создаем клиента в tracking: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setScore(scoreUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("название стратегии не равно", strategy.getTitle(), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равна", (strategy.getScore()), is(scoreUpdate));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482516")
    @DisplayName("C482516.UpdateStrategy. Успешное обновление title & score стратегии, description = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482516() {
        String title = "Стратегия Autotest 006 - Заголовок";
        String description = "Стратегия Autotest 006 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 006 - Обновленный Заголовок";
        String descriptionUpdate = null;
        Integer scoreUpdate = 5;

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

        //Создаем клиента в tracking: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), score);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(nullValue()));
        assertThat("оценка стратегии не равна", (strategy.getScore()), is(scoreUpdate));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("839319")
    @DisplayName("C839319.UpdateStrategy. Успешное обновление title & description стратегии, score = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839319() {
        String title = "Стратегия Autotest 007 - Заголовок";
        String description = "Стратегия Autotest 007 - Описание";
        Integer score = 1;
        String titleUpdate = "Стратегия Autotest 007 - Обновленный Заголовок";
        String descriptionUpdate = "Стратегия Autotest 007 - Обновленное Описание";
        Integer scoreUpdate = null;

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

        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, score);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());

        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("оценка стратегии не равна", (strategy.getScore()), is(nullValue()));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("839399")
    @DisplayName("C839399.UpdateStrategy. Успешное обновление стратегии description & score = null")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C839399() {
        String title = "Стратегия Autotest 008 - Заголовок";
        String description = "Стратегия Autotest 008 - Описание";
        Integer score = 1;
        String descriptionUpdate = null;
        Integer scoreUpdate = null;

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

        //Создаем клиента в tracking: client, contract, strategy в статусе draft
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null, score);

        //Формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        updateStrategyRequest.setScore(scoreUpdate);
        //Вызываем метод updateStrategy
        Response responseUpdateStrategy = strategyApi.updateStrategy()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .body(updateStrategyRequest)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(nullValue()));
        assertThat("оценка стратегии не равна", (strategy.getScore()), is(nullValue()));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    //*** Методы для работы тестов ***
    //Метод создает клиента, договор и стратегию в БД автоследования: tracking.client / tracking.contract / tracking.strategy
    void createClientWithContractAndStrategy(UUID investId, SocialProfile socialProfile, String contractId,
                                             ContractRole contractRole, ContractState contractState, UUID strategyId,
                                             String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {

        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);

        //Создаем запись в таблице tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);

        //Создаем запись в таблице tracking.strategy
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(score);
        strategy = trackingService.saveStrategy(strategy);
    }

    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    @Step("Перемещение offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> receiverBytes.receiveBatch(topic.getName(),
                Duration.ofSeconds(3), BytesValue.class), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }
}