package socialTrackingNotification;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static ru.qa.tinkoff.kafka.Topics.*;

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
public class SocialTrackingNotificationErrorTest {

    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    SocialTrackingNotificationSteps socialTrackingNotificationSteps;

    String contractIdSlave;
    UUID investIdSlave;
    UUID strategyId;
    Long subscriptionId = 228L;
    Long subscriptionBlockId = 4L;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        strategyId = UUID.randomUUID();
        String sienelIdSlave = stpSiebel.siebelIdSocialTrackingNotification;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = socialTrackingNotificationSteps.getBrokerAccounts(sienelIdSlave);
        investIdSlave = resAccountMaster.getInvestId();
        contractIdSlave = resAccountMaster.getBrokerAccounts().get(0).getId();
        socialTrackingNotificationSteps.deleteDataFromDb(contractIdSlave, investIdSlave);
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
    @AllureId("1707843")
    @DisplayName("C1707843. Не нашли контракт автоследования")
    @Subfeature("Успешные сценарии")
    @Description("handleSubscriptionBlockEvent Обработка событий об изменении состояния блокировки подписки")
    void C1707843() {
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        socialTrackingNotificationSteps.resetOffsetToEnd(NC_INPUT);
        Tracking.Event subscription = socialTrackingNotificationSteps.createSubscriptionEvent(contractIdSlave, time, strategyId,"test", subscriptionId, subscriptionBlockId);
        byte[] keyBytes = subscription.getContract().toByteArray();
        kafkaSender.send(TRACKING_SUBSCRIPTION_EVENT, keyBytes, subscription.toByteArray());
        //проверяем, что не отправили событие
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(NC_INPUT, Duration.ofSeconds(3));
        List<Pair<String, byte[]>> getMessageWithId =  messages.stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).collect(Collectors.toList());
        assertThat("Отправили событие в топик", getMessageWithId.size(), equalTo(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1707847")
    @DisplayName("C1707847. Метод сервиса счетов ответил ошибкой")
    @Subfeature("Успешные сценарии")
    @Description("handleSubscriptionBlockEvent Обработка событий об изменении состояния блокировки подписки")
    void C1707847() {
        //Добавить запись с clientId != investId из CC.
        socialTrackingNotificationSteps.createClientAndContract(UUID.randomUUID(), contractIdSlave, ClientRiskProfile.aggressive, ContractState.tracked, UUID.randomUUID());
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        socialTrackingNotificationSteps.resetOffsetToEnd(NC_INPUT);
        Tracking.Event subscription = socialTrackingNotificationSteps.createSubscriptionEvent(contractIdSlave, time, strategyId,"test", subscriptionId, subscriptionBlockId);
        byte[] keyBytes = subscription.getContract().toByteArray();
        kafkaSender.send(TRACKING_SUBSCRIPTION_EVENT, keyBytes, subscription.toByteArray());
        //проверяем, что не отправили событие
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(NC_INPUT, Duration.ofSeconds(3));
        List<Pair<String, byte[]>> getMessageWithId =  messages.stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).collect(Collectors.toList());
        assertThat("Отправили событие в топик", getMessageWithId.size(), equalTo(0));
    }

    @SneakyThrows
    @Test
    @AllureId("1707842")
    @DisplayName("C1707842.  Контракт не подключён к автоследованию")
    @Subfeature("Успешные сценарии")
    @Description("handleSubscriptionBlockEvent Обработка событий об изменении состояния блокировки подписки")
    void C1707842() {
        socialTrackingNotificationSteps.createClientAndContract(investIdSlave, contractIdSlave, ClientRiskProfile.aggressive, ContractState.untracked, null);
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        socialTrackingNotificationSteps.resetOffsetToEnd(NC_INPUT);
        Tracking.Event subscription = socialTrackingNotificationSteps.createSubscriptionEvent(contractIdSlave, time, strategyId,"test", subscriptionId, subscriptionBlockId);
        byte[] keyBytes = subscription.getContract().toByteArray();
        kafkaSender.send(TRACKING_SUBSCRIPTION_EVENT, keyBytes, subscription.toByteArray());
        //проверяем, что не отправили событие
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(NC_INPUT, Duration.ofSeconds(3));
        List<Pair<String, byte[]>> getMessageWithId =  messages.stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).collect(Collectors.toList());
        assertThat("Отправили событие в топик", getMessageWithId.size(), equalTo(0));
    }

    private static Stream<Arguments> provideParametresForMessage() {
        return Stream.of(
            Arguments.of(Tracking.Event.Action.UPDATED, false, true),
            Arguments.of(Tracking.Event.Action.UPDATED, true, false),
            Arguments.of(Tracking.Event.Action.CREATED, true , true)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideParametresForMessage")
    @AllureId("1707818")
    @DisplayName("C1707818. В событии не заполнен блок subscription.block, " +
        "C1707834 В событии не заполен subscription, " +
        "C1707833 action != 'UPDATED'")
    @Subfeature("Успешные сценарии")
    @Description("handleSubscriptionBlockEvent Обработка событий об изменении состояния блокировки подписки")
    void C1707818(Tracking.Event.Action eventAction, Boolean subscriptionBlockIsNotFilled, Boolean subscriptionIsNotFilled) {
        socialTrackingNotificationSteps.createClientAndContract(investIdSlave, contractIdSlave, ClientRiskProfile.aggressive, ContractState.tracked, strategyId);
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.Event subscription = socialTrackingNotificationSteps.createSubscriptionEvent(contractIdSlave, time, strategyId,"test", subscriptionId, subscriptionBlockId);
        Tracking.Strategy subscriptionStrategy = subscription.getSubscription().getStrategy();
        //Формируем message без subscription.block
        if (subscriptionBlockIsNotFilled.equals(false)){
            subscription = subscription.toBuilder()
                .setSubscription(Tracking.Subscription.newBuilder()
                    .setStrategy(subscriptionStrategy)
                    .setContractId(contractIdSlave)
                    .setId(subscriptionId))
                .build();
        }
        //Формируем message без subscription
        if (subscriptionIsNotFilled.equals(false)){
            subscription = subscription.toBuilder()
                .setContract(Tracking.Contract.newBuilder()
                    .setId(contractIdSlave)
                    .setState(Tracking.Contract.State.TRACKED)
                    .build())
                .clearSubscription()
                .build();
        }
        //Формируем message c action == CREATED
        if (eventAction.equals(Tracking.Event.Action.CREATED)){
            subscription = subscription.toBuilder()
                .setAction(eventAction)
                .build();
        }

        byte[] keyBytes = subscription.getSubscription().getContractIdBytes().toByteArray();
        socialTrackingNotificationSteps.resetOffsetToEnd(NC_INPUT);
        kafkaSender.send(TRACKING_SUBSCRIPTION_EVENT, keyBytes, subscription.toByteArray());
        //проверяем, что не отправили событие
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(NC_INPUT, Duration.ofSeconds(3));
        List<Pair<String, byte[]>> getMessageWithId =  messages.stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).collect(Collectors.toList());
        assertThat("Отправили событие в топик", getMessageWithId.size(), equalTo(0));
    }

}

