package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;

@Component
@RequiredArgsConstructor
public class SlaveOrderRowMapper implements RowMapper<SlaveOrder> {
    @Override
    public SlaveOrder mapRow(Row row, int rowNum) throws DriverException {
        return SlaveOrder.builder()
            .contractId(row.getString("contract_id"))
            .strategyId(row.getUUID("strategy_id"))
            .version(row.getInt("version"))
            .attemptsCount(row.getByte("attempts_count"))
            .action(row.getByte("action"))
            .classCode(row.getString("class_code"))
            .idempotencyKey(row.getUUID("idempotency_key"))
            .price(row.getDecimal("price"))
            .quantity(row.getDecimal("quantity"))
            .state(row.get("state", Byte.class))
            .ticker(row.getString("ticker"))
            .tradingClearingAccount(row.getString("trading_clearing_account"))
            .filledQuantity(row.getDecimal("filled_quantity"))
            .build();
    }

}
