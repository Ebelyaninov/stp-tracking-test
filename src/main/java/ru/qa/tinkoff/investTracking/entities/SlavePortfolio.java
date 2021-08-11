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
public class SlavePortfolio {
    private String contractId;
    private UUID strategyId;
    private Integer version;
    private Integer comparedToMasterVersion;
    private List<Position> positions;
    private BaseMoneyPosition baseMoneyPosition;
    private Date changedAt;

    @UDT(keyspace = "invest_tracking", name = "slave_portfolio_position")
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
        @Field(name = "synchronized_to_master_version")
        private Integer synchronizedToMasterVersion;
        @Field(name = "price")
        private BigDecimal price;
        @Field(name = "price_ts")
        private Date price_ts;
        @Field(name = "rate")
        private BigDecimal rate;
        @Field(name = "rate_diff")
        private BigDecimal rateDiff;
        @Field(name = "quantity_diff")
        private BigDecimal quantityDiff;
        @Field(name = "changed_at")
        private Date changedAt;
        @Field(name = "last_change_action")
        private Byte lastChangeAction;
    }

    @UDT(name = "slave_portfolio_base_money_position")
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
        @Field(name = "last_change_action")
        private Byte lastChangeAction;
    }


}
