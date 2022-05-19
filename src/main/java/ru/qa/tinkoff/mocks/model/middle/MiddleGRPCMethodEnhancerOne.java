package ru.qa.tinkoff.mocks.model.middle;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class MiddleGRPCMethodEnhancerOne implements TextResourceInfo {

    private final String agreementId;
    private final String usdUnscaledPrice;
    private final String usdScaledQty;
    private final String quantity;
    private final String ticker;
    private final String tradingAccount;
    private final String quantityCCL;
    private final String tickerCCL;
    private final String tradingAccountCCL;


    public MiddleGRPCMethodEnhancerOne (String agreementId, String usdUnscaledPrice, String usdScaledQty, String quantity, String ticker, String tradingAccount, String quantityCCL, String tickerCCL, String tradingAccountCCL) {
        this.agreementId = agreementId;
        this.usdUnscaledPrice = usdUnscaledPrice;
        this.usdScaledQty = usdScaledQty;
        this.quantity = quantity;
        this.ticker = ticker;
        this.tradingAccount = tradingAccount;
        this.quantityCCL = quantityCCL;
        this.tickerCCL = tickerCCL;
        this.tradingAccountCCL = tradingAccountCCL;

    }

    @Override
    public String path() {
        return "mockTemplate/middleGrpcMethodTemplateOne";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("agreementId", agreementId,
            "usdUnscaledPrice", usdUnscaledPrice, "usdScaledQty", usdScaledQty,
            "quantity", quantity, "ticker", ticker, "tradingAccount", tradingAccount,
            "quantityCCL", quantityCCL, "tickerCCL", tickerCCL, "tradingAccountCCL", tradingAccountCCL);
    }

}
