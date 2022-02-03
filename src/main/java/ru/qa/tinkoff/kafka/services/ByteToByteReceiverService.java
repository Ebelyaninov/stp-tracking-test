package ru.qa.tinkoff.kafka.services;

import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
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
public class ByteToByteReceiverService {
    public static final Duration TIMEOUT_MILLS = Duration.ofMillis(1500);

    private final BoostedReceiver<byte[], byte[]> boostedReceiver;
//    private final BoostedReceiverImpl<byte[], byte[]> receiver;

    public void resetOffsetToLatest(Topics topic) {
        resetOffsetToLatest(topic, TIMEOUT_MILLS);
    }

    @Step("Переместить offset топика {topic.name} до текущей позиции")
    public void resetOffsetToLatest(Topics topic, Duration pollTimeout) {
        log.info("Полечен запрос на вычитавание всех сообщений из Kafka топика {} ",
            topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> boostedReceiver.receiveBatch(topic.getName()), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }

    public  List<Pair<byte[], byte[]>> receiveBatch(Topics topic) {
        return receiveBatch(topic, TIMEOUT_MILLS);
    }

    @Step("Получить сообщения из Kafka топика {topic.name}")
    public List<Pair<byte[], byte[]>> receiveBatch(Topics topic, Duration pollTimeout) {
        log.info("Поступил запрос на поиск новых сообщений в Kafka топике {}", topic.getName());
        String topicName = topic.getName();
        List<Pair<byte[], byte[]>> result = boostedReceiver
            .receiveBatchWithKeys(topicName, pollTimeout);
        log.info("Из Kafka топика {} получено сообщений: {}", topicName, result.size());
        return result;
    }


//        @Step("Переместить offset для всех партиций Kafka топика {topic.name} в конец очереди")
//        public void resetOffsetToEnd(Topics topic) {
//            log.info("Сброс offset для топика {}", topic.getName());
//            receiver.getKafkaConsumer().subscribe(Collections.singletonList(topic.getName()));
//            receiver.getKafkaConsumer().poll(Duration.ofSeconds(5));
//            Map<TopicPartition, Long> endOffsets = receiver.getKafkaConsumer()
//                .endOffsets(receiver.getKafkaConsumer().assignment());
//            HashMap<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
//            endOffsets.forEach((p, o) -> {
//                log.info("Для partition: {} последний offset: {}", p.partition(), o);
//                offsets.put(p, new OffsetAndMetadata(o));
//            });
//            receiver.getKafkaConsumer().commitSync(offsets);
//
//            log.info("Offset для всех партиций Kafka топика {} перемещены в конец очереди", topic.getName());
//            receiver.getKafkaConsumer().unsubscribe();
//
//    }

    /**
     * Вычитывает сообщения из Kafka до тех пор пока не получит необходимое количество
     *
     * @param topic        Kafka топик
     * @param messageCount необходимое количество сообщений
     * @return список сообщений в формате String
     * @throws org.awaitility.core.ConditionTimeoutException если не смогли получить из Kafka нужно количество сообщений
     */
    @Step("Получить сообщения из Kafka топика {topic.name}")
    public List<byte[]> receiveBatchStrict(Topics topic, Integer messageCount) {
        log.info("Поступил запрос на поиск новых сообщений в Kafka топике {}", topic.getName());
        ArrayList<byte[]> result = new ArrayList<>();

        await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() -> {
            result.addAll(boostedReceiver.receiveBatch(topic.getName()));
            return result.size() >= messageCount;
        });

        log.info("Из Kafka топика {} получено {} сообщений", topic.getName(), result.size());
        return result;
    }



}
