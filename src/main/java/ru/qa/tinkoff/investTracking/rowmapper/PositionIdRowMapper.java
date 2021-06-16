package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.PositionId;

@Component
public class PositionIdRowMapper implements RowMapper<PositionId> {
    @Override
    public PositionId mapRow(Row row, int rowNum) throws DriverException {
        return new PositionId(row.getString("ticker"), row.getString("trading_clearing_account"));
    }
}
