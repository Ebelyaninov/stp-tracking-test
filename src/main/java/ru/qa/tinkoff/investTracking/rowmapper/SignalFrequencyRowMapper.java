package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SignalFrequency;
import org.springframework.data.cassandra.core.cql.RowMapper;

@Component
public class SignalFrequencyRowMapper  implements RowMapper<SignalFrequency> {

    @Override
    public SignalFrequency mapRow(Row row, int i) throws DriverException {
        return SignalFrequency.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .count(row.getInt("count"))
            .build();
    }
}
