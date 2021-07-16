package ru.qa.tinkoff.tracking.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Data
@Validated
@ConfigurationProperties(prefix = "grpc.middle")
public class GrpcServiceConfigurationProperties {
    @NotNull
    private String url;
    @NotNull
    private int port;
}
