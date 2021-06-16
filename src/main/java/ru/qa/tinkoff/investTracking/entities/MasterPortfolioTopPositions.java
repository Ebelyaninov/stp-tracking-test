package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Builder
@Getter
@EqualsAndHashCode
public class MasterPortfolioTopPositions {
    private UUID strategyId;
    private Date cut;
    private List<TopPosition> positions;
}
