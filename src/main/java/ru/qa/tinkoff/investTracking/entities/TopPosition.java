package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class TopPosition {
    private final String ticker;
    private final String tradingClearingAccount;
    private final Integer signalsCount;
}
