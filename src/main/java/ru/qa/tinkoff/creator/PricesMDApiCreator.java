package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.MD.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class PricesMDApiCreator  extends MarketDataCreator<PricesApi>{
    private final RestClientApiConfigurationProperties properties;
    @Override
    public PricesApi get() {
        return ApiClient.api(getConfig(properties.getMdApiBaseUrl())).prices();

    }
}
