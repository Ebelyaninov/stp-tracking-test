package ru.qa.tinkoff.kafka.kafkaClient;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

import java.util.HashMap;
import java.util.Map;

public class KafkaHelper {

    public <K, V> KafkaMessageListenerContainer<K, V> createContainer(String topicName,
                                                                      MessageListener<K, V> message,
                                                                      Class<? extends Deserializer<K>> keySerializerClass,
                                                                      Class<? extends Deserializer<V>> valueSerializerClass) {
        ContainerProperties containerProps = new ContainerProperties(topicName);
        containerProps.setMessageListener(message);

        Map<String, Object> props = consumerProps(keySerializerClass, valueSerializerClass);
        DefaultKafkaConsumerFactory<K, V> cf = new DefaultKafkaConsumerFactory<>(props);
        return new KafkaMessageListenerContainer<>(cf, containerProps);
    }

    public KafkaTemplate<byte[], byte[]> createByteToByteTemplate() {
        return createTemplate(ByteArraySerializer.class, ByteArraySerializer.class);
    }

    public KafkaTemplate<String, String> createStringToStringTemplate() {
        return createTemplate(StringSerializer.class, StringSerializer.class);
    }

    public KafkaTemplate<String, byte[]> createStringToByteTemplate() {
        return createTemplate(StringSerializer.class, ByteArraySerializer.class);
    }

    public <K, V> KafkaTemplate<K, V> createTemplate(Class<? extends Serializer<K>> keySerializerClass,
                                                     Class<? extends Serializer<V>> valueSerializerClass) {
        Map<String, Object> senderProps = senderProps(keySerializerClass, valueSerializerClass);
        ProducerFactory<K, V> pf = new DefaultKafkaProducerFactory<>(senderProps);
        return new KafkaTemplate<>(pf);
    }

    private Map<String, Object> consumerProps(Class<?> keySerializer, Class<?> valueSerializer) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "vm-kafka-stp01t.tcsbank.ru:9092,vm-kafka-stp02t.tcsbank.ru:9092,vm-kafka-stp03t.tcsbank.ru:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "foo");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "15000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keySerializer);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueSerializer);
        return props;
    }

    private Map<String, Object> senderProps(Class<?> keySerializer, Class<?> valueSerializer) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "vm-kafka-stp01t.tcsbank.ru:9092,vm-kafka-stp02t.tcsbank.ru:9092,vm-kafka-stp03t.tcsbank.ru:9092");
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        return props;
    }

}
