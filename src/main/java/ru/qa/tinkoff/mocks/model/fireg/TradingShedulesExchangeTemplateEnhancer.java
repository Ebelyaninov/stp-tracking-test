package ru.qa.tinkoff.mocks.model.fireg;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class TradingShedulesExchangeTemplateEnhancer implements TextResourceInfo {

    private final String exchange;
    private final String currentDate;
    private final String currentDatePlusOne;
    private final String currentDatePlusTwo;

    public TradingShedulesExchangeTemplateEnhancer (String exchange, String currentDate, String currentDatePlusOne, String  currentDatePlusTwo) {
        this.exchange = exchange;
        this.currentDate = currentDate;
        this.currentDatePlusOne = currentDatePlusOne;
        this.currentDatePlusTwo = currentDatePlusTwo;
    }

    @Override
    public String path() {
        return "mockTemplate/tradingShedulesExchangeTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("exchange", exchange, "currentDate", currentDate, "currentDatePlusOne", currentDatePlusOne, "currentDatePlusTwo", currentDatePlusTwo);
    }
}
