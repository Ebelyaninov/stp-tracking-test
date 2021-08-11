package ru.qa.tinkoff.investTracking.entities;

import com.datastax.driver.mapping.annotations.Field;
import com.datastax.driver.mapping.annotations.UDT;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Builder
@Getter
@EqualsAndHashCode
public class MasterPortfolioTopPositions {
    private UUID strategyId;
    private Date cut;
    private List<MasterPortfolioTopPositions.TopPositions> positions;

    @UDT(keyspace = "invest_tracking", name = "master_portfolio_top_position")
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @Getter
    @EqualsAndHashCode
    public static class TopPositions {
        @Field(name = "ticker")
        private String ticker;
        @Field(name = "trading_clearing_account")
        private String tradingClearingAccount;
        @Field(name = "signals_count")
        private int signalsCount;
    }
}
