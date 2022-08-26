package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.CADBClientAnalytic.api.ClientAnalyticApi;
import ru.qa.tinkoff.swagger.CADBClientAnalytic.invoker.ApiClient;



@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class ClientAnalyticCadbCreator extends ApiCADBClientAnalyticCreator<ClientAnalyticApi>{
    private final RestClientApiConfigurationProperties properties;
    @Override
    public ClientAnalyticApi get() {
        return ApiClient.api(getConfig(properties.getCadbClientAnalyticApiBaseUrl())).clientAnalytic();
    }
}
