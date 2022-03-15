package ru.qa.tinkoff.api.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Data
@Validated
@ConfigurationProperties(prefix = "app.rest-client")
public class RestClientApiConfigurationProperties {

    @NotBlank
    private String trackingApiBaseUri;
    @NotBlank
    private String trackingApiAdminBaseUri;

    private String investAccountPublicApiBaseUrl;

    private String mdApiBaseUrl;

    private String trackingCacheSlaveBaseUrl;

    private String trackingCacheApiBaseUrl;

    private String trackingApiSocialStrategyBaseUrl;


}
