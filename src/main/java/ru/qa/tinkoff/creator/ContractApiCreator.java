package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;

import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;

import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;


@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class ContractApiCreator extends ApiCreator<ContractApi>  {
    private final RestClientApiConfigurationProperties properties;
    private final Environment env;
    @Override
    public ContractApi get() {
        return ApiClient.api(getConfig(properties.getTrackingApiBaseUri())).contract();
    }
}
