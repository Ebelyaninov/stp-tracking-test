package ru.qa.tinkoff.investTracking.entities;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class MasterPortfolioRate {
    private UUID strategyId;
    private Date cut;
    private Map<String, BigDecimal> typeToRateMap;
    private Map<String, BigDecimal> sectorToRateMap;
    private Map<String, BigDecimal> companyToRateMap;
}