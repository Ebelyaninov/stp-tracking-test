package ru.qa.tinkoff.kafka.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiver;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiverImpl;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSender;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSenderImpl;

import java.util.Properties;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;

@Configuration
@ComponentScan("ru.qa.tinkoff.kafka.oldkafkaservice")
@Import({JacksonAutoConfiguration.class})
@EnableConfigurationProperties(KafkaOldConfigurationProperties.class)
public class KafkaOldConfiguration {

    @Bean("oldKafkaProperties")
    public Properties oldKafkaProperties(KafkaOldConfigurationProperties config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getServers());
        props.put("group.id", "social_game_autotests");
        props.put("enable.auto.commit", "false");
        props.put("acks", "all");
        props.put(AUTO_OFFSET_RESET_CONFIG, "latest");
        return props;
    }

    @Bean("oldKafkaStringSender")
    public BoostedSender<String, String> oldKafkaStringSender(@Qualifier("oldKafkaProperties") Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean("oldKafkaByteArraySender")
    public BoostedSender<String, byte[]> oldKafkaByteArraySender(@Qualifier("oldKafkaProperties") Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean("oldKafkaByteToByteSender")
    public BoostedSender<byte[], byte[]> oldKafkaByteToByteSender(@Qualifier("oldKafkaProperties") Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean("oldKafkaStringReceiver")
    public BoostedReceiver<String, String> oldKafkaStringReceiver(@Qualifier("oldKafkaProperties") Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

    @Bean("oldKafkaByteArrayReceiver")
    public BoostedReceiver<String, byte[]> oldKafkaByteArrayReceiver(@Qualifier("oldKafkaProperties") Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

    @Bean("oldKafkaByteReceiver")
    public BoostedReceiver<byte[], byte[]> oldKafkaByteReceiver(@Qualifier("oldKafkaProperties") Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

}
