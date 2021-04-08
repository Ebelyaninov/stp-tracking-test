package ru.qa.tinkoff.kafka.configuration;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.tinkoff.invest.sdet.kafka.KafkaConfigurationProperties;
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;

import java.util.Properties;

@Configuration
@ComponentScan("ru.qa.tinkoff.kafka.services")
@Import({KafkaProtobufFactoryAutoConfiguration.class, JacksonAutoConfiguration.class})
public class KafkaAutoConfiguration {

    @Bean
    @ConditionalOnClass(StringSerializer.class)
    public Properties kafkaStringProperties(KafkaConfigurationProperties config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getServers());
        props.put("acks", "all");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return props;
    }

    @Bean
    public StringSenderService kafkaSender(Properties kafkaStringProperties) {
        return new StringSenderService(kafkaStringProperties);
    }
}