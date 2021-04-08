package ru.qa.tinkoff.investTracking.entities;

import com.datastax.driver.mapping.annotations.Field;
import com.datastax.driver.mapping.annotations.UDT;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Builder
@ToString
@Getter
public class MasterPortfolio {
    private final String contractId;
    private final UUID strategyId;
    private final Integer version;
    private final List<Position> positions;
    private final BaseMoneyPosition baseMoneyPosition;

    @UDT(name = "master_portfolio_position")
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @Getter
    public static class Position {
        @Field(name = "ticker")
        private String ticker;
        @Field(name = "trading_clearing_account")
        private String tradingClearingAccount;
        @Field(name = "quantity")
        private BigDecimal quantity;
        @Field(name = "changed_at")
        private Date changedAt;
        @Field(name = "last_change_detected_version")
        private Integer lastChangeDetectedVersion;
        @Field(name = "last_change_action")
        private Byte lastChangeAction;
    }

    @UDT(name = "master_portfolio_base_money_position")
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @Getter
    public static class BaseMoneyPosition {
        @Field(name = "quantity")
        private BigDecimal quantity;

        @Field(name = "changed_at")
        private Date changedAt;
    }
}
