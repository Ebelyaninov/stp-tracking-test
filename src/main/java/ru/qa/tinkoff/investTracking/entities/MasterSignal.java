package ru.qa.tinkoff.investTracking.entities;


import lombok.*;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.Date;

@Builder
@ToString
@Getter
public class MasterSignal {
    private final UUID strategyId;
    private final Integer version;
    private final Byte state;
    private final String ticker;
    private final String tradingClearingAccount;
    private final Byte action;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final Date createdAt;
    private final BigDecimal tailOrderQuantity;

}
