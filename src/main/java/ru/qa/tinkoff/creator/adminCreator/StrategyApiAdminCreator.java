package ru.qa.tinkoff.creator.adminCreator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class StrategyApiAdminCreator extends ApiAdminCreator<StrategyApi>{
    private final RestClientApiConfigurationProperties properties;

    @Override
    public ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi get() {
        return ApiClient.api(getConfig(properties.getTrackingApiAdminBaseUri())).strategy();
    }
}
