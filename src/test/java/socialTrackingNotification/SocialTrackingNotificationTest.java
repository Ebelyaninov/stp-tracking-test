package socialTrackingNotification;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringReceiverService;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.socialTrackingNotification.SocialTrackingNotificationSteps;
import ru.qa.tinkoff.steps.socialTrackingNotification.SocialTrackingNotificationStepsConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ClientRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.tinkoff.trading.tracking.Tracking;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.NC_INPUT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SUBSCRIPTION_EVENT;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("Обработка событий об изменении состояния блокировки подписки")
@Feature("ISTAP-2051")
@DisplayName("social-tracking-notification")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Owner("ext.ebelyaninov")
@Tags({@Tag("social-tracking-notification"),@Tag("handleSubscriptionBlockEvent")})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    SocialTrackingNotificationStepsConfiguration.class
})
public class SocialTrackingNotificationTest {

        @Autowired
        ClientService clientService;
        @Autowired
        ContractService contractService;
        @Autowired
        StpSiebel stpSiebel;
        @Autowired
        ByteToByteSenderService kafkaSender;
        @Autowired
        SocialTrackingNotificationSteps socialTrackingNotificationSteps;
        @Autowired
        StringReceiverService stringReceiverService;

        String contractIdSlave;
        UUID investIdSlave;
        UUID strategyId;
        Long subscriptionId = 228L;
        Long subscriptionBlockId = 4L;
        String sibelId;

        @BeforeAll
        void getdataFromInvestmentAccount() {
            strategyId = UUID.randomUUID();
            String sienelIdSlave = stpSiebel.siebelIdSocialTrackingNotification;
            //получаем данные по клиенту master в api сервиса счетов
            GetBrokerAccountsResponse resAccountMaster = socialTrackingNotificationSteps.getBrokerAccounts(sienelIdSlave);
            investIdSlave = resAccountMaster.getInvestId();
            contractIdSlave = resAccountMaster.getBrokerAccounts().get(0).getId();
            sibelId = socialTrackingNotificationSteps.getActualSiebel(investIdSlave);
        }

        @AfterEach
        void deleteClient() {
            step("Удаляем клиента автоследования", () -> {
                try {
                    contractService.deleteContract(contractService.getContract(contractIdSlave));
                } catch (Exception e) {
                }
                try {
                    clientService.deleteClient(clientService.getClient(investIdSlave));
                } catch (Exception e) {
                }
            });
        }

    @SneakyThrows
    @Test
    @AllureId("1707817")
    @DisplayName("C1707817. Отправляем событие об приостановки следования")
    @Subfeature("Успешные сценарии")
    @Description("handleSubscriptionBlockEvent Обработка событий об изменении состояния блокировки подписки")
    void C1707817() {
        socialTrackingNotificationSteps.createClientAndContract(investIdSlave, contractIdSlave, ClientRiskProfile.aggressive, ContractState.tracked, UUID.randomUUID());
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.Event subscription = socialTrackingNotificationSteps.createSubscriptionEvent(contractIdSlave, time, strategyId,"test", subscriptionId, subscriptionBlockId);
        byte[] keyBytes = subscription.getContract().toByteArray();
        socialTrackingNotificationSteps.resetOffsetToEnd(NC_INPUT);
        kafkaSender.send(TRACKING_SUBSCRIPTION_EVENT, keyBytes, subscription.toByteArray());

        Optional<Pair<String, String>> getMessageWithId;
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).pollInterval(Duration.ofMillis(200))
            .until(() -> stringReceiverService.receiveBatch(NC_INPUT, Duration.ofSeconds(1)).stream()
                .filter(key -> key.getKey().equals(contractIdSlave)).findFirst(), notNullValue());

        List<Pair<String,String>> messages = stringReceiverService.receiveBatch(NC_INPUT, Duration.ofSeconds(4));
        getMessageWithId =  messages.stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).findFirst();
        //Проверяем данные в комманде
        String valueFromMessage = getMessageWithId.get().getValue();
        //Проверяем key
        assertThat("Key != " + contractIdSlave, getMessageWithId.get().getKey(), equalTo(contractIdSlave));
        //Проверяем данные из события
        checkMessageParametres(valueFromMessage, subscription, time);
    }

    @SneakyThrows
    @Test
    @AllureId("1707849")
    @DisplayName("C1707849. Отправляем событие об возобновлению следования")
    @Subfeature("Успешные сценарии")
    @Description("handleSubscriptionBlockEvent Обработка событий об изменении состояния блокировки подписки")
    void C1707849() {
        socialTrackingNotificationSteps.createClientAndContract(investIdSlave, contractIdSlave, ClientRiskProfile.aggressive, ContractState.tracked, UUID.randomUUID());
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        Tracking.Event subscription = socialTrackingNotificationSteps.createSubscriptionEventWithBlockedEndDate(contractIdSlave, time, strategyId,"test", subscriptionId, subscriptionBlockId);
        byte[] keyBytes = subscription.getContract().toByteArray();
        socialTrackingNotificationSteps.resetOffsetToEnd(NC_INPUT);
        kafkaSender.send(TRACKING_SUBSCRIPTION_EVENT, keyBytes, subscription.toByteArray());

        Optional<Pair<String, String>> getMessageWithId;
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).pollInterval(Duration.ofMillis(200))
            .until(() -> stringReceiverService.receiveBatch(NC_INPUT, Duration.ofSeconds(1)).stream()
                .filter(key -> key.getKey().equals(contractIdSlave)).findFirst(), notNullValue());
        List<Pair<String,String>> messages = stringReceiverService.receiveBatch(NC_INPUT, Duration.ofSeconds(4));
        getMessageWithId =  messages.stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).findFirst();
        //Проверяем данные в комманде
        String valueFromMessage = getMessageWithId.get().getValue();
        //Проверяем key
        assertThat("Key != " + contractIdSlave, getMessageWithId.get().getKey(), equalTo(contractIdSlave));
        //Проверяем данные из события
        checkMessageParametres(valueFromMessage, subscription, time);
    }

    void checkMessageParametres(String valueFromMessage, Tracking.Event subscription, OffsetDateTime time){
        String eventName = String.valueOf(JsonPath.from(valueFromMessage).getString("eventName"));
        String eventId = String.valueOf(JsonPath.from(valueFromMessage).getString("eventId"));
        String createdBy = String.valueOf(JsonPath.from(valueFromMessage).getString("createdBy"));
        String createdAt = String.valueOf(JsonPath.from(valueFromMessage).getString("createdAt"));
        String siebelId = String.valueOf(JsonPath.from(valueFromMessage).getString("payload.contact.siebelId"));
        String brokerId = String.valueOf(JsonPath.from(valueFromMessage).getString("payload.brokerId"));
        String name = String.valueOf(JsonPath.from(valueFromMessage).getString("payload.strategy.name"));
        String createdAtFromMessage = time.toInstant().atOffset(ZoneOffset.ofHours(0)).toString().substring(0, 23) + "+00:00";
        //если в событии subscription.block.period.ended_at = null (подписка была заблокирована): 'TradingStrategyStop'
        if (subscription.getSubscription().getBlock().getPeriod().getEndedAt().toString().equals("")) {
            assertThat("eventName != TradingStrategyStop", eventName, equalTo("TradingStrategyStop"));
        }
        else {
            //иначе (подписка была разблокирована): 'TradingStrategyRenovation'
            assertThat("eventName != TradingStrategyRenovation", eventName, equalTo("TradingStrategyRenovation"));
        }
        //Проверяем данные из события
        assertThat("eventId != subscription.block.id из входного события", eventId, equalTo(String.valueOf(subscription.getSubscription().getBlock().getId())));
        assertThat("createdBy != social-tracking-notification", createdBy, equalTo("social-tracking-notification"));
        assertThat("createdAt != created_at из входного события", createdAt, equalTo(createdAtFromMessage));
        assertThat("payload.contact.siebelId != actualSiebelId из ответа от invest-account-public-api", siebelId, equalTo(sibelId));
        assertThat("payload.brokerId != subscription.contract_id из входного события", brokerId, equalTo(subscription.getSubscription().getContractId()));
        assertThat("payload.strategy.name != subscription.contract_id из входного события", name, equalTo(subscription.getSubscription().getStrategy().getTitle()));
    }

}
