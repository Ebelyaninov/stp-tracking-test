package ru.qa.tinkoff.creator;

import io.restassured.builder.RequestSpecBuilder;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;

import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static ru.qa.tinkoff.swagger.MD.invoker.JacksonObjectMapper.jackson;

public abstract class FiregInstrumentsCreator<T> {
    public abstract T get();

    protected ru.qa.tinkoff.swagger.fireg.invoker.ApiClient.Config getConfig(String baseUrl) {
        return ApiClient.Config.apiConfig()
            .reqSpecSupplier(() -> new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setConfig(config().objectMapperConfig(objectMapperConfig().defaultObjectMapper(jackson()))));
    }
}
