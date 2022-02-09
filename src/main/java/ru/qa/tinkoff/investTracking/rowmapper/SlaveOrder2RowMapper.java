package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;

@Component
@RequiredArgsConstructor
public class SlaveOrder2RowMapper implements RowMapper<SlaveOrder2> {
    @Override
    public SlaveOrder2 mapRow(Row row, int rowNum) throws DriverException {
        return SlaveOrder2.builder()
            .contractId(row.getString("contract_id"))
            .createAt((row.get("created_at", java.util.Date.class)))
            .action(row.getByte("action"))
            .attemptsCount(row.getInt("attempts_count"))
            .classCode(row.getString("class_code"))
            .filledQuantity(row.getDecimal("filled_quantity"))
            .id(row.getUUID("id"))
            .idempotencyKey(row.getUUID("idempotency_key"))
            .price(row.getDecimal("price"))
            .quantity(row.getDecimal("quantity"))
            .state(row.get("state", Byte.class))
            .strategyId(row.getUUID("strategy_id"))
            .ticker(row.getString("ticker"))
            .tradingClearingAccount(row.getString("trading_clearing_account"))
            .version(row.getInt("version"))
            .comparedToMasterVersion(row.get("compared_to_master_version", Integer.class))
            .build();
    }
}
