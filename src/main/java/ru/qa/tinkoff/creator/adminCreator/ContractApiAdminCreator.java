package ru.qa.tinkoff.creator.adminCreator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.api.configuration.RestClientApiConfigurationProperties;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;

@EnableConfigurationProperties(RestClientApiConfigurationProperties.class)
@Component
@RequiredArgsConstructor
public class ContractApiAdminCreator extends ApiAdminCreator<ContractApi>{
    private final RestClientApiConfigurationProperties properties;
    @Override
    public ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi get() {
        return ApiClient.api(getConfig(properties.getTrackingApiAdminBaseUri())).contract();
    }
}
