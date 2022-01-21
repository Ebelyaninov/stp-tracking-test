package ru.qa.tinkoff.creator;

import io.restassured.builder.RequestSpecBuilder;
import ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient;

import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.JacksonObjectMapper.jackson;

public abstract class ApiCacheSlaveCreator<T> {
    public abstract T get();

    protected ApiClient.Config getConfig(String baseUri) {
        return ApiClient.Config.apiConfig()
            .reqSpecSupplier(() -> new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setConfig(config().objectMapperConfig(objectMapperConfig().defaultObjectMapper(jackson()))));
    }
}
