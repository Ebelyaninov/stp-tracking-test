package ru.qa.tinkoff.api.configuration;

import io.restassured.builder.RequestSpecBuilder;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;

import java.util.function.Supplier;

import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static ru.qa.tinkoff.swagger.tracking.invoker.JacksonObjectMapper.jackson;

@Configuration
@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@AllArgsConstructor
public class TrackingApiClientConfiguration {

    private final RestClientApiConfigurationProperties properties;

    @Bean
    public ApiClient apiClient() {
        return ApiClient.api(getConfig(properties.getTrackingApiBaseUri()));
    }

    @Bean
    public Supplier<StrategyApi> strategyApiSupplier(ApiClient apiClient) {
        return apiClient::strategy;
    }

    private static ApiClient.Config getConfig(String baseUri) {
        // Создание RequestSpecBuilder скопировано из ApiClient.Config,
        // с единстенным отличием - переопределятся url для доступа к rest-сервису.
        return ApiClient.Config.apiConfig()
            .reqSpecSupplier(() -> new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setConfig(config().objectMapperConfig(objectMapperConfig().defaultObjectMapper(jackson()))));
    }

}