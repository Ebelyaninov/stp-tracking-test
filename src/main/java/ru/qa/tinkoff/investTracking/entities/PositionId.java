package ru.qa.tinkoff.investTracking.entities;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@AllArgsConstructor
@Getter
public class PositionId {
    private final String ticker;
    private final String tradingClearingAccount;

    @Override
    public String toString() {
        return "PositionId{" +
            "ticker='" + ticker + '\'' +
            ", tradingClearingAccount='" + tradingClearingAccount + '\'' +
            '}';
    }
}
