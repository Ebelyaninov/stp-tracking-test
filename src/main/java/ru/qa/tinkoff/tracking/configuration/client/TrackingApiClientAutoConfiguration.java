package ru.qa.tinkoff.tracking.configuration.client;


import org.springframework.context.annotation.Configuration;

@Configuration
public class TrackingApiClientAutoConfiguration {
//
//    @Bean
//    public ApiClient trackignApiClient(FeignAllureAdapter feignAllureAdapter) {
//        ApiClient apiClient = new ApiClient();
//        apiClient.getFeignBuilder().client(new feign.httpclient.ApacheHttpClient());
//        apiClient.addAuthorization("Allure", feignAllureAdapter);
//        return apiClient;
//    }
//
//    @Bean
//    public ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient trackingAdminApiClient(FeignAllureAdapter feignAllureAdapter) {
//        ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient apiClient = new ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient();
//        apiClient.getFeignBuilder().client(new feign.httpclient.ApacheHttpClient());
//        apiClient.addAuthorization("Allure", feignAllureAdapter);
//        return apiClient;
//    }
//
//    @Bean
//    public ContractApi contractApiClient(ApiClient trackignApiClient) {
//        return trackignApiClient.buildClient(ContractApi.class);
//    }
//
//    @Bean
//    public StrategyApi strategyApi(ApiClient trackignApiClient) {
//        return trackignApiClient.buildClient(StrategyApi.class);
//    }
//
//    @Bean
//    public ClientApi clientApi(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient trackingAdminApiClient) {
//        return trackingAdminApiClient.buildClient(ClientApi.class);
//    }
//
//    @Bean
//    public ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi strategyApi (ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient trackingAdminApiClient) {
//        return trackingAdminApiClient.buildClient(ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi.class);
//    }
//
//    @Bean
//    public FeignAllureAdapter feignAllureAdapter() {
//        return new FeignAllureAdapter();
//    }

}
