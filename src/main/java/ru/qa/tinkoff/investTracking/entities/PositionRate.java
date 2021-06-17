package ru.qa.tinkoff.investTracking.entities;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PositionRate {
    private String text;
    private BigDecimal decimal;
}
