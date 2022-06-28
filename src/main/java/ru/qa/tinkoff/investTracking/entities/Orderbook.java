package ru.qa.tinkoff.investTracking.entities;

import com.datastax.driver.core.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Date;

@Builder
@ToString
@Getter
public class Orderbook {
    private final String instrumentId;
    private final LocalDate endedAtDate;
    private final Date endedAt;
    private final Date startedAt;
    private final Double bidMinimumLots;
    private final Double askMinimumLots;

}
