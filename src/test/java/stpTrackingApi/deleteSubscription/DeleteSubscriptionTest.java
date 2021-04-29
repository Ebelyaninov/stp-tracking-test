package stpTrackingApi.deleteSubscription;


import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("deleteSubscription - Удаление подписки на торговую стратегию")
@Feature("TAP-7383")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class})
public class DeleteSubscriptionTest {
    private KafkaHelper kafkaHelper = new KafkaHelper();

    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;

    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client clientMaster;
    Contract contractMaster;
    Strategy strategyMaster;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    Profile profile;
    String siebelIdMaster = "5-11PX4UETD";
    String siebelIdSlave = "5-7ECGV169";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategyMaster);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {
            }

        });
    }

    @Test
    @AllureId("535360")
    @DisplayName("C535360.DeleteSubscription.Удаление подписки на торговую стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C535360() throws Exception {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        //создаем данные для стратегии
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        LocalDateTime dateCreateTr = null;
        Tracking.Event event = null;
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
        try (KafkaMessageConsumer<byte[], byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.event",
                     ByteArrayDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            subscriptionApi.deleteSubscription()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .xTcsSiebelIdHeader(siebelIdSlave)
                .contractIdQuery(contractIdSlave)
                .strategyIdPath(strategyId)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(ResponseBodyData::asString);
            //смотрим, сообщение, которое поймали в топике kafka
            KafkaMessageConsumer.Record<byte[], byte[]> record = messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Сообщение не получено"));
            Thread.sleep(20000);
            List<KafkaMessageConsumer.Record<byte[], byte[]>> records = messageConsumer.listRecords();

//            event = Tracking.Event.parseFrom(record.value);
            event = Tracking.Event.parseFrom(records.get(records.size() - 1).value);
            dateCreateTr = Instant.ofEpochSecond(event.getCreatedAt().getSeconds(), event.getCreatedAt().getNanos())
                .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        }
        //проверяем, данные в сообщении
        assertThat("тип события не равен", event.getAction().toString(), is("DELETED"));
        assertThat("ID договора не равен", event.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("ID стратегии не равен", uuid(event.getSubscription().getStrategy().getId()), is(strategyId));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("inactive"));
        assertThat("время удаления подписки не равно", subscription.getEndTime().toLocalDateTime(), is(dateCreateTr));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("untracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(IsNull.nullValue()));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
    }


    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(String SIEBEL_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()));
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        strategyMaster = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    public String uuidString(ByteString bytes) {
        return uuid(bytes).toString();
    }


}
