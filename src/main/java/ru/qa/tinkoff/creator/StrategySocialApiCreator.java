package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class StrategySocialApiCreator extends ApiSocialStrategyCreator<StrategyApi> {
    private final RestClientApiConfigurationProperties properties;
    private final Environment env;

    @Override
    public StrategyApi get() {
        String trackingApiSocialStrategyBaseUrl = properties.getTrackingApiSocialStrategyBaseUrl();
        ApiClient.Config config = getConfig(trackingApiSocialStrategyBaseUrl);
        return ApiClient.api(config).strategy();



    }
}
