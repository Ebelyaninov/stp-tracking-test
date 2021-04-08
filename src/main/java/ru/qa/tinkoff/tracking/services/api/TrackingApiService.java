//package ru.qa.tinkoff.tracking.services.api;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.qameta.allure.Allure;
//import io.qameta.allure.Step;
//import lombok.Builder;
//import lombok.Data;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
//import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
//import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest;
//import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse;
//import ru.qa.tinkoff.swagger.tracking.model.GetUntrackedContractsResponse;
//import ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest;
//
//import java.util.UUID;
//
//@Slf4j
////@Component
//public class TrackingApiService {
//    private final ContractApi contractApi;
//    private final ObjectMapper objectMapper;
//    private final StrategyApi strategyApi;
//
//
//    public TrackingApiService(ContractApi contractApi, ObjectMapper objectMapper, StrategyApi strategyApi) {
//        this.contractApi = contractApi;
//        this.objectMapper = objectMapper;
//        this.strategyApi = strategyApi;
//
//    }
//
//    @SneakyThrows
//    @Step("Получение списка доступных договоров")
//    public GetUntrackedContractsResponse getUntrackedContracts(GetUntrackedContactsRequestBuilder builder) {
//        GetUntrackedContractsResponse response = contractApi.getUntrackedContracts(
//            builder.getName(),
//            builder.getVersion(),
//            builder.getPlatform(),
//            builder.getSiebelId(),
//            builder.getDeviceId()
//        );
//        Allure.addAttachment("Ответ от сервиса", "application/json",
//            objectMapper.writeValueAsString(response));
//        return response;
//    }
//
//    @Data
//    @Builder
//    public static class GetUntrackedContactsRequestBuilder {
//        private String name;
//        private String version;
//        private String platform;
//        private String siebelId;
//        private String deviceId;
//    }
//
//
//    @SneakyThrows
//    @Step("Получение списка доступных договоров")
//    public CreateStrategyResponse createStrategy (CreateStrategyRequestData requestData) {
//
//        CreateStrategyResponse response = strategyApi.createStrategy(
//            requestData.getName(),
//            requestData.getVersion(),
//            requestData.getPlatform(),
//            requestData.getSiebelId(),
//            requestData.getRequest(),
//            requestData.getDeviceId());
//        Allure.addAttachment("Ответ от сервиса", "application/json",
//            objectMapper.writeValueAsString(response));
//        return response;
//    }
//
//    @Data
//    @Builder
//    public static class CreateStrategyRequestData {
//        private String name;
//        private String version;
//        private String platform;
//        private String siebelId;
//        private String deviceId;
//        private CreateStrategyRequest request;
//    }
//
//
//    @SneakyThrows
//    @Step("Обновление параметров стратегии ведущим")
//    public UpdateStrategyRequest updateStrategy (UpdateStrategyRequestRequestData requestData) {
////        Allure.addAttachment("Ответ от сервиса", "application/json",
////            objectMapper.writeValueAsString(response));
//        return new UpdateStrategyRequest();
//    }
////
////
//    @Data
//    @Builder
//    public static class UpdateStrategyRequestRequestData {
//        private String name;
//        private String version;
//        private String platform;
//        private String siebelId;
//        private String deviceId;
//        private UUID strategyId;
//        private UpdateStrategyRequest request;
//    }
//}
