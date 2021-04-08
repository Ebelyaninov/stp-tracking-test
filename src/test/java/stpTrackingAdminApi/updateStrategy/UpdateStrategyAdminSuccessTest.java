package stpTrackingAdminApi.updateStrategy;

import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
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
import ru.tinkoff.trading.tracking.Tracking;

import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("updateStrategy - Обновление стратегии администратором")
@Feature("TAP-7225")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class})
public class UpdateStrategyAdminSuccessTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client client;
    Contract contract;
    Strategy strategy;
    String SIEBEL_ID = "4-1UBHYQ63";
    private KafkaHelper kafkaHelper = new KafkaHelper();

    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ContractService сontractService;
    @Autowired
    ClientService clientService;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {}
            try {
                сontractService.deleteContract(contract);
            } catch (Exception e) {}
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {}
        });
    }

    @Test
    @AllureId("482510")
    @DisplayName("C482510.UpdateStrategy.Успешное обновление стратегии админом, статус 'active'")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482510() throws Exception {
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 001"+ dateNow;
        String descriptionUpdate = "new test стратегия autotest 001";
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
        //создаем клиента в tracking: client, contract, strategy
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        try (KafkaMessageConsumer<byte[], byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.event",
                     ByteArrayDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(7000);
            messageConsumer.startUp();
           //вызываем метод updateStrategy
            Response responseUpdateStrategy = strategyApi.updateStrategy()
                .reqSpec(r -> r.addHeader("api-key", "tracking"))
                .xAppNameHeader("invest")
                .xTcsLoginHeader("tracking_admin")
                .strategyIdPath(strategyId.toString())
                .body(updateStrategyRequest)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response);
            //проверяем, что в response есть заголовки x-trace-id и x-server-time
            assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
            assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
            //смотрим, сообщение, которое поймали в топике kafka
            KafkaMessageConsumer.Record<byte[], byte[]> record = messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Сообщение не получено"));
            Thread.sleep(5000);
            messageConsumer.await();
            List<KafkaMessageConsumer.Record<byte[], byte[]>> records = messageConsumer.listRecords();
            for (int i = 0; i < records.size(); i++) {
                Tracking.Event eventBefore = Tracking.Event.parseFrom(records.get(i).value);
                if ((titleUpdate.equals(eventBefore.getStrategy().getTitle()))
//                if ((strategyId.toString().equals((uuid(eventBefore.getStrategy().getId())).toString()))
                    & ("UPDATED".equals(eventBefore.getAction().toString()))) {
                    event = eventBefore;
                    break;
                }
            }
            //парсим сообщение из топика tracking.event
//            event = Tracking.Event.parseFrom(record.value);
//            event = Tracking.Event.parseFrom(records.get(records.size() - 1).value);
            dateCreateTr = Instant.ofEpochSecond(event.getCreatedAt().getSeconds(), event.getCreatedAt().getNanos())
                .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        }
//        String dateResponse = responseHeader.substring(0, 19);
//        String dateFromEvent = dateCreateTr.format(formatter);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID стратегии не равен", uuid(event.getStrategy().getId()), is(strategyId));
        assertThat("название стратегии не равен", (event.getStrategy().getTitle()), is(titleUpdate));
//        assertThat("дата изменения стратегии не равна", (dateFromEvent), is(dateResponse));
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482511")
    @DisplayName("C482511.UpdateStrategy.Успешное обновление стратегии админом, статус 'draft'")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482511() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 002";
        String descriptionUpdate = "new test стратегия autotest 002";
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
        //создаем клиента в tracking: client, contract, strategy
        createClientWithContractStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
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
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("482513")
    @DisplayName("C482513.UpdateStrategy.Успешное обновление стратегии админом, только description")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482513() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String descriptionUpdate = "new test стратегия autotest 003";
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
        //создаем клиента в tracking: client, contract, strategy
        createClientWithContractStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
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
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }


    @Test
    @AllureId("482514")
    @DisplayName("C482514.UpdateStrategy.Успешное обновление стратегии админом, только title")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482514() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 004";
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
        //создаем клиента в tracking: client, contract, strategy
        createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        //вызываем метод updateStrategy
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
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("active"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("482516")
    @DisplayName("C482516.UpdateStrategy.Успешное обновление стратегии админом, description = null")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет администратору обновить параметры стратегии независимо от ее статуса.")
    void C482516() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String titleUpdate = "тест стратегия autotest 005";
        String descriptionUpdate = null;
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
        //создаем клиента в tracking: client, contract, strategy
        createClientWithContractStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
        //формируем body для метода updateStrategy
        UpdateStrategyRequest updateStrategyRequest = new UpdateStrategyRequest();
        updateStrategyRequest.setTitle(titleUpdate);
        updateStrategyRequest.setDescription(descriptionUpdate);
        //вызываем метод updateStrategy
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
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseUpdateStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(titleUpdate));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.USD.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(descriptionUpdate));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(StrategyRiskProfile.AGGRESSIVE.toString()));
    }

/////////***методы для работы тестов**************************************************************************

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, SocialProfile socialProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = сontractService.saveContract(contract);
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
            .setScore(1);
        strategy = trackingService.saveStrategy(strategy);
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractStrategy(UUID investId, SocialProfile socialProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = сontractService.saveContract(contract);
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date);
        strategy = trackingService.saveStrategy(strategy);
    }

    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }
}
