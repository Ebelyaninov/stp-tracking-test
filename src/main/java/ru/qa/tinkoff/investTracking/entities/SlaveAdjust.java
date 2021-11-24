package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Getter
@Builder
public class SlaveAdjust {
    private String contractId;
    private UUID strategyId;
    private Date createdAt;
    private Long operationId;
    private BigDecimal quantity;
    private String currency;
    private Boolean deleted;
    private Date changedAt;
}
