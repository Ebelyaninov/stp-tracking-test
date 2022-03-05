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
    private final String usdScaledQty;
    private final String quantityAAPL;
    private final String ticker;
    private final String tradingAccount;

    public MiddleGRPCMethodEnhancer (String agreementId, String eurUnscaledPrice, String rubUnscaledPrice, String usdUnscaledPrice, String usdScaledQty, String quantityAAPL, String ticker, String tradingAccount) {
        this.agreementId = agreementId;
        this.eurUnscaledPrice = eurUnscaledPrice;
        this.rubUnscaledPrice = rubUnscaledPrice;
        this.usdUnscaledPrice = usdUnscaledPrice;
        this.usdScaledQty = usdScaledQty;
        this.quantityAAPL = quantityAAPL;
        this.ticker = ticker;
        this.tradingAccount = tradingAccount;
    }

    @Override
    public String path() {
        return "mockTemplate/middleGrpcMethodTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("agreementId", agreementId, "eurUnscaledPrice", eurUnscaledPrice, "rubUnscaledPrice", rubUnscaledPrice,
                      "usdUnscaledPrice", usdUnscaledPrice, "usdScaledQty", usdScaledQty,"quantityAAPL", quantityAAPL, "ticker", ticker, "tradingAccount", tradingAccount);
    }
}
