package ru.qa.tinkoff.investTracking.entities;

import com.datastax.driver.mapping.annotations.Field;
import com.datastax.driver.mapping.annotations.UDT;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Builder
@ToString
@Getter
@Value
public class Dividend {
    private final String contractId;
    private final UUID strategyId;
    private final Long id;
    private final Dividend.Context context;


    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class ExchangePositionId {
        private String ticker;
        private String tradingClearingAccount;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class Context {
        private int version;
        private BigDecimal amount;
        private ExchangePositionId exchangePositionId;
        private Date createdAt;
    }
}
