package ru.qa.tinkoff.mocks.model.fireg;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class GetDividendAAPLTemplateEnhancer implements TextResourceInfo {

    private final String paymentDatePlusDay;
    private final String dividendNet;
    private final String paymentDate;
    private final String lastBuyDate;
    private final String paymentDateMinusEightDays;


    public GetDividendAAPLTemplateEnhancer(String paymentDatePlusDay, String dividendNet, String paymentDate, String  lastBuyDate, String paymentDateMinusEightDays) {
        this.paymentDatePlusDay = paymentDatePlusDay;
        this.dividendNet = dividendNet;
        this.paymentDate = paymentDate;
        this.lastBuyDate = lastBuyDate;
        this.paymentDateMinusEightDays = paymentDateMinusEightDays;
    }

    @Override
    public String path() {
        return "mockTemplate/getDividendAAPL";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("paymentDatePlusDay", paymentDatePlusDay, "dividendNet", dividendNet, "paymentDate", paymentDate, "lastBuyDate", lastBuyDate, "paymentDateMinusEightDays", paymentDateMinusEightDays);
    }
}
