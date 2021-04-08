package ru.qa.tinkoff.kafka.kafkaClient;

import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class KafkaMessageConsumer<K, V> implements AutoCloseable {

    private final KafkaHelper kafkaHelper;
    private final String topicName;
    private final Class<? extends Deserializer<K>> keyDeserializerClass;
    private final Class<? extends Deserializer<V>> valDeserializerClass;

    private final Record<K, V> record = new Record<>();
    private final List<Record<K, V>> records = new ArrayList<>();

    private CountDownLatch countDownLatch;
    private KafkaMessageListenerContainer<K, V> containerListener;
    private int timeout = 1000;


    public KafkaMessageConsumer(KafkaHelper kafkaHelper,
                                String topicName,
                                Class<? extends Deserializer<K>> keyDeserializerClass,
                                Class<? extends Deserializer<V>> valDeserializerClass) {
        this.kafkaHelper = kafkaHelper;
        this.topicName = topicName;
        this.keyDeserializerClass = keyDeserializerClass;
        this.valDeserializerClass = valDeserializerClass;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @SneakyThrows
    public void startUp() {
        countDownLatch = new CountDownLatch(1);
        MessageListener<K, V> messageListener = data -> {
            record.key = data.key();
            record.value = data.value();
            countDownLatch.countDown();

            Record<K, V> r = new Record<>();
            r.key = data.key();
            r.value = data.value();
            records.add(r);
        };

        containerListener = kafkaHelper.createContainer(topicName,
            messageListener, keyDeserializerClass, valDeserializerClass);
        containerListener.setBeanName("testAuto");
        containerListener.start();
        Thread.sleep(timeout);
    }

    @SneakyThrows
    public Optional<Record<K, V>> await() {
        if (!countDownLatch.await(1000, TimeUnit.MILLISECONDS)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public List<Record<K, V>> listRecords() {
        return Collections.unmodifiableList(records);
    }

    @Override
    public void close() {
        containerListener.stop();
    }

    public static class Record<K, V> {
        public K key;
        public V value;
    }
}