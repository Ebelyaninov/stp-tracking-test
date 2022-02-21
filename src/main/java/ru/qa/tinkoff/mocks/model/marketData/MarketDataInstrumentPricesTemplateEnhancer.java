package ru.qa.tinkoff.mocks.model.marketData;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class MarketDataInstrumentPricesTemplateEnhancer implements TextResourceInfo {

    private final String path;
    private final String type;
    private final String instrumentId;
    private final String ts;
    private final String priceValue;

    public MarketDataInstrumentPricesTemplateEnhancer(String path, String type, String instrumentId, String ts, String priceValue) {
        this.path = path;
        this.type = type;
        this.instrumentId = instrumentId;
        this.ts = ts;
        this.priceValue = priceValue;
    }

    @Override
    public String path() {
        return "mockTemplate/marketDataInstrumentPricesTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("path", path, "type", type, "instrumentId", instrumentId, "ts", ts, "priceValue", priceValue);
    }
}
