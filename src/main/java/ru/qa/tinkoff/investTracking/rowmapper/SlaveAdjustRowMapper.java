package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlaveAdjust;

@Component
@RequiredArgsConstructor
public class SlaveAdjustRowMapper implements RowMapper<SlaveAdjust> {

    @Override
    public SlaveAdjust mapRow(Row row, int i) throws DriverException {
        return SlaveAdjust.builder()
            .contractId(row.getString("contract_id"))
            .strategyId(row.getUUID("strategy_id"))
            .changedAt(row.getTimestamp("changed_at"))
            .quantity(row.getDecimal("quantity"))
            .currency(row.getString("currency"))
            .deleted(row.getBool("deleted"))
            .operationId(row.getLong("operation_id"))
            .createdAt(row.getTimestamp("created_at"))
            .build();

    }
}
