package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.StrategyTailDiffRate;
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;

@Component
public class StrategyTailDiffRateRowMapper implements RowMapper<StrategyTailDiffRate> {
    @Override
    public StrategyTailDiffRate mapRow(Row row, int i) throws DriverException {
        return StrategyTailDiffRate.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .values(row.getMap("values", Float.class, Float.class))
            .build();
    }
}
