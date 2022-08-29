package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
@Getter
@Builder
@ToString
public class SlaveOrder2 {
    private final String contractId;
    private final Date createAt;
    private final Byte action;
    private final Integer attemptsCount;
    private final String classCode;
    private final Integer comparedToMasterVersion;
    private final BigDecimal filledQuantity;
    private final UUID id;
    private final UUID idempotencyKey;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Byte state;
    private final UUID strategyId;
    private final String ticker;
    private final String tradingClearingAccount;
    private final Integer version;
    private final UUID positionId;
    private final String orderId;










}
