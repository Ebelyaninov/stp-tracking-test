package ru.qa.tinkoff.investTracking.entities;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@AllArgsConstructor
@Getter
public class PositionDateFromFireg {
    private final String ticker;
    private final String tradingClearingAccount;
    private final String type;
    private final String sector;
    private final String company;
}
