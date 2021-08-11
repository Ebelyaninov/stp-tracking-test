package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;


@Component
public class StrategyTailValueRowMapper  implements RowMapper<StrategyTailValue> {
    @Override
    public StrategyTailValue mapRow(Row row, int i) throws DriverException {
        return StrategyTailValue.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .value(row.getDecimal("value"))
            .build();
    }
}
