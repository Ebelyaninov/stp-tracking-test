package ru.qa.tinkoff.investTracking.entities;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
public class ManagementFee {
    private String contractId;
    private UUID strategyId;
    private Long subscriptionId;
    private Integer version;
    private Date settlementPeriodStartedAt;
    private Date settlementPeriodEndedAt;
    private Context context;
}
