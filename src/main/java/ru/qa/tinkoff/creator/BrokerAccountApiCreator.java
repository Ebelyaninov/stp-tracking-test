package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;

import ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class BrokerAccountApiCreator extends InvestAccountCreator<BrokerAccountApi> {
    private final RestClientApiConfigurationProperties properties;

    @Override
    public ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi get() {
        return ApiClient.api(getConfig(properties.getInvestAccountPublicApiBaseUrl())).brokerAccount();

    }
}
