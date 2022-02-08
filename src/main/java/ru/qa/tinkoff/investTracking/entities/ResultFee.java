package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
@Data
@Builder
public class ResultFee {
    private String contractId;
    private UUID strategyId;
    private Long subscriptionId;
    private Integer version;
    private Date settlementPeriodStartedAt;
    private Date settlementPeriodEndedAt;
    private Context context;
    private BigDecimal highWaterMark;
    private Date  createdAt;
}
