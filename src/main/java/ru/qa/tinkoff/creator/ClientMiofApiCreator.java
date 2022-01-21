package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.miof.invoker.ApiClient;
import ru.qa.tinkoff.swagger.miof.api.ClientApi;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class ClientMiofApiCreator extends MiofApiCreator<ClientApi>{
    private final RestClientApiConfigurationProperties properties;

    @Override
    public ru.qa.tinkoff.swagger.miof.api.ClientApi get() {
        return ApiClient.api(getConfig(properties.getMdApiBaseUrl())).client();

    }
}
