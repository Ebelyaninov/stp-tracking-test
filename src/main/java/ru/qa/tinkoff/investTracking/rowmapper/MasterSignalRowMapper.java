package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;


@Component
public class MasterSignalRowMapper implements RowMapper<MasterSignal> {

    @Override
    public MasterSignal mapRow(Row row, int i) throws DriverException {
        return MasterSignal.builder()
            .strategyId(row.getUUID("strategy_id"))
            .version(row.getInt("version"))
            .state(row.get("state", Byte.class))
            .ticker(row.getString("ticker"))
            .tradingClearingAccount(row.getString("trading_clearing_account"))
            .action(row.get("action", Byte.class))
            .quantity(row.getDecimal("quantity"))
            .price(row.getDecimal("price"))
            .createdAt(row.get("created_at", java.util.Date.class))
            .build();
    }
}
