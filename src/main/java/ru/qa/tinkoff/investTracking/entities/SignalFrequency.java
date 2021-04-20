package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Date;
import java.util.UUID;

@Getter
@ToString
@Builder
@EqualsAndHashCode
public class SignalFrequency {
    private UUID strategyId;
    private Date cut;
    private Integer count;
}
