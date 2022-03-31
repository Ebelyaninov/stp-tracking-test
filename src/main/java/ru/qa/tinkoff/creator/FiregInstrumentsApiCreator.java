package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class FiregInstrumentsApiCreator extends FiregInstrumentsCreator<InstrumentsApi>{
    private final RestClientApiConfigurationProperties properties;
    @Override
    public InstrumentsApi get() {
        return ApiClient.api(getConfig(properties.getTradingFiregApiBaseUrl())).instruments();
    }
}
