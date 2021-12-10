package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
@Getter
@ToString
@Builder
@EqualsAndHashCode
public class StrategyTailDiffRate {
    private UUID strategyId;
    private Date cut;
    private Map<Float, Float> values;

}
