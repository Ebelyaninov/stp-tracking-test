package ru.qa.tinkoff.kafka.configuration;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiver;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiverImpl;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSender;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSenderImpl;

import java.util.Properties;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;

@Configuration
@ComponentScan("ru.qa.tinkoff.kafka.services")
@Import({JacksonAutoConfiguration.class})
@EnableConfigurationProperties(KafkaConfigurationProperties.class)
public class KafkaAutoConfiguration {

    private static final String PRODUCER_SASL_JAAS_CONFIG =
        "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%login%\" password=\"%password%\";";

    @Bean
    @Primary
    public Properties kafkaStringProperties(KafkaConfigurationProperties config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getServers());
        props.put("group.id", "tracking.tap-tracking-tests");
        props.put("enable.auto.commit", "false");
        props.put("acks", "all");
        props.put(AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put("sasl.mechanism", "SCRAM-SHA-512");
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.jaas.config",
            PRODUCER_SASL_JAAS_CONFIG
                .replace("%login%", config.getLogin())
                .replace("%password%", config.getPassword()));
        return props;
    }

    @Bean
    @Primary
    public BoostedSender<String, String> kafkaStringSender(Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean
    @Primary
    public BoostedSender<String, byte[]> kafkaByteArraySender(Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean
    @Primary
    public BoostedSender<byte[], byte[]> kafkaByteToByteSender(Properties kafkaProperties) {
        return new BoostedSenderImpl<>(kafkaProperties) {
        };
    }

    @Bean
    @Primary
    public BoostedReceiver<String, String> kafkaStringReceiver(Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

    @Bean
    @Primary
    public BoostedReceiverImpl<String, byte[]> kafkaByteArrayReceiver(Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }

    @Bean
    @Primary
    public BoostedReceiver<byte[], byte[]> kafkaByteReceiver(Properties kafkaProperties) {
        return new BoostedReceiverImpl<>(kafkaProperties) {
        };
    }


}
