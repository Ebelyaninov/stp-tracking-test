package ru.qa.tinkoff.steps.socialTrackingNotification;

import com.google.protobuf.Timestamp;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.SiebelApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetActualSiebelResponse;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.ClientRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiverImpl;
import ru.tinkoff.trading.tracking.Tracking;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.awaitility.Awaitility.await;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialTrackingNotificationSteps {

    UtilsTest utilsTest = new UtilsTest();

    private final BoostedReceiverImpl<String, byte[]> boostedReceiver;

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    SiebelApi siebelApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).siebel();

    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;

    Contract contractSlave;
    Client clientSlave;

    private final ByteArrayReceiverService kafkaReceiver;

    public void createClientAndContract(UUID investId, String contractId, ClientRiskProfile riskProfile, ContractState contractState, UUID strategyId) {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
    }

    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    public String getActualSiebel (UUID investId) {
        GetActualSiebelResponse getActualSiebelResponse = siebelApi.getActualSiebel()
            .investIdPath(investId)
            .xAppNameHeader("tracking")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetActualSiebelResponse.class));
        String siebelId = getActualSiebelResponse.getActualSiebelId();
        return siebelId;
    }

    public Tracking.Event createSubscriptionEvent (String contractIdSlave, OffsetDateTime time, UUID strategyId, String strategyTitle, Long subscriptionId, Long subscriptionBlockId){

        Tracking.Event subscription = Tracking.Event.newBuilder()
            .setId(utilsTest.buildByteString(UUID.randomUUID()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setSubscription(Tracking.Subscription.newBuilder()
                .setStrategy(Tracking.Strategy.newBuilder()
                    .setId(utilsTest.buildByteString(strategyId))
                    .setTitle(strategyTitle)
                    .build())
                .setContractId(contractIdSlave)
                .setId(subscriptionId)
                .setBlock(Tracking.SubscriptionBlock.newBuilder()
                    .setId(subscriptionBlockId)
                    .setPeriod(Tracking.TimestampPeriod.newBuilder()
                        .setStartedAt(Timestamp.newBuilder()
                            .setSeconds(time.toEpochSecond())
                            .setNanos(time.getNano())
                            .build())
                        .build())
                    .build())
                .build())
            .build();
        return subscription;
    }

    public Tracking.Event createSubscriptionEventWithBlockedEndDate (String contractIdSlave, OffsetDateTime time, UUID strategyId, String strategyTitle, Long subscriptionId, Long subscriptionBlockId){

        Tracking.Event subscription = Tracking.Event.newBuilder()
            .setId(utilsTest.buildByteString(UUID.randomUUID()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setSubscription(Tracking.Subscription.newBuilder()
                .setStrategy(Tracking.Strategy.newBuilder()
                    .setId(utilsTest.buildByteString(strategyId))
                    .setTitle(strategyTitle)
                    .build())
                .setContractId(contractIdSlave)
                .setId(subscriptionId)
                .setBlock(Tracking.SubscriptionBlock.newBuilder()
                    .setId(subscriptionBlockId)
                    .setPeriod(Tracking.TimestampPeriod.newBuilder()
                        .setStartedAt(Timestamp.newBuilder()
                            .setSeconds(time.toEpochSecond())
                            .setNanos(time.getNano())
                            .build())
                        .setEndedAt(Timestamp.newBuilder()
                            .setSeconds(time.plusHours(1).toEpochSecond())
                            .setNanos(time.plusHours(1).getNano())
                            .build())
                        .build())
                    .build())
                .build())
            .build();
        return subscription;
    }

    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }

    @Step("Переместить offset для всех партиций Kafka топика {topic.name} в конец очереди")
    public void resetOffsetToEnd(Topics topic) {
        log.info("Сброс offset для топика {}", topic.getName());

        boostedReceiver.getKafkaConsumer().subscribe(Collections.singletonList(topic.getName()));
        boostedReceiver.getKafkaConsumer().poll(Duration.ofSeconds(5));
        Map<TopicPartition, Long> endOffsets = boostedReceiver.getKafkaConsumer()
            .endOffsets(boostedReceiver.getKafkaConsumer().assignment());
        HashMap<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        endOffsets.forEach((p, o) -> {
            log.info("Для partition: {} последний offset: {}", p.partition(), o);
            offsets.put(p, new OffsetAndMetadata(o));
        });
        boostedReceiver.getKafkaConsumer().commitSync(offsets);
        log.info("Offset для всех партиций Kafka топика {} перемещены в конец очереди", topic.getName());
        boostedReceiver.getKafkaConsumer().unsubscribe();
    }
}
