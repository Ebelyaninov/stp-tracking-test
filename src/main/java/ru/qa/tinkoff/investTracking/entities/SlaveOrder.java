package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

@Getter
@Builder
public class SlaveOrder {
    private final String contractId;
    private final UUID strategyId;
    private final Integer version;
    private final Byte attemptsCount;
    private final Byte action;
    private final String classCode;
    private final UUID idempotencyKey;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Byte state;
    private final String ticker;
    private final String tradingClearingAccount;
    private final BigDecimal filledQuantity;
    private final Date createAt;
}
