package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;

@Component
public class MasterPortfolioValueRowMapper implements RowMapper<MasterPortfolioValue> {
    @Override
    public MasterPortfolioValue mapRow(Row row, int i) throws DriverException {
        return MasterPortfolioValue.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .value(row.getDecimal("value"))
            .build();
    }
}
