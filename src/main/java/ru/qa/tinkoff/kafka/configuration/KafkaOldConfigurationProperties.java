package ru.qa.tinkoff.kafka.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Data
@Validated
@ConfigurationProperties(prefix = "app.kafka-old")

public class KafkaOldConfigurationProperties {
    @NotBlank
    private String servers;
    @NotBlank
    private String schemaRegistryUrl;
    private String login;
    private String password;

}
