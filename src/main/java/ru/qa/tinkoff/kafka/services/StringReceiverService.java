package ru.qa.tinkoff.kafka.services;

import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiver;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiverImpl;

import java.time.Duration;
import java.util.*;

import static org.awaitility.Awaitility.await;

@Slf4j
@Service
@RequiredArgsConstructor
public class StringReceiverService {

    public static final Duration TIMEOUT_MILLS = Duration.ofMillis(1500);
    private final BoostedReceiver<String, String> boostedReceiver;



    public void resetOffsetToLatest(Topics topic) {
        resetOffsetToLatest(topic, TIMEOUT_MILLS);
    }

    @Step("Переместить offset топика {topic.name} до текущей позиции")
    public void resetOffsetToLatest(Topics topic, Duration pollTimeout) {
        log.info("Полечен запрос на вычитавание всех сообщений из Kafka топика {} ",
            topic.getName());
        await().atMost(Duration.ofSeconds(pollTimeout.getSeconds()))
            .until(() -> boostedReceiver
                .receiveBatch(topic.getName()), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }



    @Step("Получить сообщения из Kafka топика {topic.name}")
    public List<Pair<String,String>> receiveBatch(Topics topic, Duration pollTimeout) {
        String topicName = topic.getName();
        List<Pair<String,String>> result = boostedReceiver.receiveBatchWithKeys(topicName, pollTimeout);
        log.info("Из Kafka топика {} получено сообщений: {}", topicName, result.size());
        return result;



    }

    /**
     * Вычитывает сообщения из Kafka до тех пор пока не получит необходимое количество
     *
     * @param topic        Kafka топик
     * @param messageCount необходимое количество сообщений
     * @return список сообщений в формате String
     * @throws org.awaitility.core.ConditionTimeoutException если не смогли получить из Kafka нужно количество сообщений
     */
    @Step("Получить сообщения из Kafka топика {topic.name}")
    public List<String> receiveBatchStrict(Topics topic, Integer messageCount) {
        log.info("Поступил запрос на поиск новых сообщений в Kafka топике {}", topic.getName());
        ArrayList<String> result = new ArrayList<>();

        await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() -> {
            result.addAll(boostedReceiver.receiveBatch(topic.getName()));
            return result.size() >= messageCount;
        });

        log.info("Из Kafka топика {} получено {} сообщений", topic.getName(), result.size());
        return result;
    }

}
