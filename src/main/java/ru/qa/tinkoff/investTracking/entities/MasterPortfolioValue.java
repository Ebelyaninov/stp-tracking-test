package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
@Getter
@ToString
@Builder
@EqualsAndHashCode
public class MasterPortfolioValue {
    private UUID strategyId;
    private Date cut;
    private BigDecimal value;
}
