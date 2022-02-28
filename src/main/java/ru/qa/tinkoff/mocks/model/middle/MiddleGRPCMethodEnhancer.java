package ru.qa.tinkoff.mocks.model.middle;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class MiddleGRPCMethodEnhancer implements TextResourceInfo {

    private final String agreementId;
    private final String eurUnscaledPrice;
    private final String rubUnscaledPrice;
    private final String usdUnscaledPrice;
    private final String quantityAAPL;

    public MiddleGRPCMethodEnhancer (String agreementId, String eurUnscaledPrice, String rubUnscaledPrice, String usdUnscaledPrice, String quantityAAPL) {
        this.agreementId = agreementId;
        this.eurUnscaledPrice = eurUnscaledPrice;
        this.rubUnscaledPrice = rubUnscaledPrice;
        this.usdUnscaledPrice = usdUnscaledPrice;
        this.quantityAAPL = quantityAAPL;
    }

    @Override
    public String path() {
        return "mockTemplate/middleGrpcMethodTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("agreementId", agreementId, "eurUnscaledPrice", eurUnscaledPrice, "rubUnscaledPrice", rubUnscaledPrice,
                      "usdUnscaledPrice", usdUnscaledPrice, "quantityAAPL", quantityAAPL);
    }
}
