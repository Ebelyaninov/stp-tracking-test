package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.trackingApiCache.api.CacheApi;
import ru.qa.tinkoff.swagger.trackingApiCache.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class CacheApiApiCreator extends ApiCacheApiCreator<CacheApi> {
    private final RestClientApiConfigurationProperties properties;

    @Override
    public ru.qa.tinkoff.swagger.trackingApiCache.api.CacheApi get() {
        return ApiClient.api(getConfig(properties.getTrackingCacheApiBaseUrl())).cache();
    }
}
