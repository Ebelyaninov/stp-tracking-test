package ru.qa.tinkoff.investTracking.entities;

import com.datastax.driver.core.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Builder
@ToString
@Getter
public class Orderbook {
    private final String instrumentId;
    private Date endedAtDate;
    private Date endedAt;
    private Date startedAt;
    private Double bidMinimumLots;
    private Double askMinimumLots;

}
