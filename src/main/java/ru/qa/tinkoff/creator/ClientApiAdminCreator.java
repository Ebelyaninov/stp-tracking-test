package ru.qa.tinkoff.creator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.api.ClientApi;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class ClientApiAdminCreator extends ApiAdminCreator<ClientApi>{
    private final RestClientApiConfigurationProperties properties;
    private final Environment env;
    @Override
    public ru.qa.tinkoff.swagger.tracking_admin.api.ClientApi get() {
        return ApiClient.api(getConfig(properties.getTrackingApiAdminBaseUri())).client();
    }

}
