package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class CacheApiSlaveCreator extends ApiCacheSlaveCreator<CacheApi>{
    private final RestClientApiConfigurationProperties properties;

    @Override
    public ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi get() {
        return ApiClient.api(getConfig(properties.getTrackingCacheSlaveBaseUrl())).cache();
    }
}
