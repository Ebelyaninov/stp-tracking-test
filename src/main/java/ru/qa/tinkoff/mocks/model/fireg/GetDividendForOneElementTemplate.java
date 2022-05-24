package ru.qa.tinkoff.mocks.model.fireg;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class GetDividendForOneElementTemplate  implements TextResourceInfo {
    private final String ticker;
    private final String classCode;
    private final String dividendId;
    private final String instrumentId;
    private final String dividendNet;
    private final String dividendCurrency;
    private final String paymentDate;
    private final String lastBuyDate;
    private final String status;


    public GetDividendForOneElementTemplate(String ticker, String classCode, String dividendId, String  instrumentId, String dividendNet,
                                            String dividendCurrency, String paymentDate, String lastBuyDate, String status) {
        this.ticker = ticker;
        this.classCode = classCode;
        this.dividendId = dividendId;
        this.instrumentId = instrumentId;
        this.dividendNet = dividendNet;
        this.dividendCurrency = dividendCurrency;
        this.paymentDate = paymentDate;
        this.lastBuyDate = lastBuyDate;
        this.status = status;
    }

    @Override
    public String path() {
        return "mockTemplate/getDividendWithOneTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("ticker", ticker, "classCode", classCode, "dividendId", dividendId, "instrumentId", instrumentId, "dividendNet", dividendNet,
            "dividendCurrency", dividendCurrency, "paymentDate", paymentDate, "lastBuyDate", lastBuyDate, "status", status);
    }
}
