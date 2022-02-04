package ru.qa.tinkoff.mocks.model.middle;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class MiddleGRPCMethodEnhancer implements TextResourceInfo {

    private final String agreementId;
    private final String usdUnscaledPrice;

    public MiddleGRPCMethodEnhancer (String agreementId, String usdUnscaledPrice) {
        this.agreementId = agreementId;
        this.usdUnscaledPrice = usdUnscaledPrice;
    }

    @Override
    public String path() {
        return "mockTemplate/middleGrpcMethodTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("agreementId", agreementId, "usdUnscaledPrice", usdUnscaledPrice);
    }
}
