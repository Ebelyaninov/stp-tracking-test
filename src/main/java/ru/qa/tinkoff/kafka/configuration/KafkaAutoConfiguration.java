package ru.qa.tinkoff.kafka.configuration;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiver;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiverImpl;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSender;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSenderImpl;

import java.util.Properties;

@Configuration
@ComponentScan("ru.qa.tinkoff.kafka.services")
@Import({JacksonAutoConfiguration.class})
@EnableConfigurationProperties(KafkaConfigurationProperties.class)
public class KafkaAutoConfiguration {

    @Bean
    @Primary
    public Properties kafkaStringProperties(KafkaConfigurationProperties config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getServers());
        props.put("group.id", "social_game_autotests");
        props.put("enable.auto.commit", "false");
        props.put("acks", "all");
        return props;
    }

    @Bean
    public BoostedSender<String, String> kafkaStringSender(Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean
    public BoostedSender<String, byte[]> kafkaByteArraySender(Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean
    public BoostedSender<byte[], byte[]> kafkaByteToByteSender(Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean
    public BoostedReceiver<String, String> kafkaStringReceiver(Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

    @Bean
    public BoostedReceiver<String, byte[]> kafkaByteArrayReceiver(Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

    @Bean
    public BoostedReceiver<byte[], byte[]> kafkaByteReceiver(Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }
}