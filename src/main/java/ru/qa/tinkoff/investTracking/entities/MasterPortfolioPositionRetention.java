package ru.qa.tinkoff.investTracking.entities;

import com.datastax.driver.mapping.annotations.Field;
import lombok.*;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Builder
@ToString
@Getter
public class MasterPortfolioPositionRetention {
    private UUID strategyId;
    private Date cut;
    private String value;
}
