package ru.qa.tinkoff.investTracking.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Context {
    private BigDecimal portfolioValue;
    private List<Positions> positions;
    private List<String> notChargedReasons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Positions {
        private String ticker;
        private String tradingClearingAccount;
        private BigDecimal quantity;
        private BigDecimal price;
        private Date priceTs;
    }

}
