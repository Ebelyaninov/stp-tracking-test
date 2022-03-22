package ru.qa.tinkoff.mocks.model.fireg;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class TradingShedulesExchangeSetTimeTemplate implements TextResourceInfo {
    private final String startTimeNow;
    private final String exchange;
    private final String currentDate;
    private final String currentDatePlusOne;
    private final String currentDatePlusTwo;

    public TradingShedulesExchangeSetTimeTemplate(String startTimeNow, String exchange, String currentDate, String currentDatePlusOne, String  currentDatePlusTwo) {
        this.startTimeNow = startTimeNow;
        this.exchange = exchange;
        this.currentDate = currentDate;
        this.currentDatePlusOne = currentDatePlusOne;
        this.currentDatePlusTwo = currentDatePlusTwo;
    }

    @Override
    public String path() {
        return "mockTemplate/tradingShedulesExchangeSetTimeTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("startTimeNow", startTimeNow,"exchange", exchange, "currentDate", currentDate, "currentDatePlusOne", currentDatePlusOne, "currentDatePlusTwo", currentDatePlusTwo);
    }
}
