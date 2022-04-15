package ru.qa.tinkoff.investTracking.rowmapper;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.Orderbook;

@Component
public class OrderbookRowMapper  implements RowMapper<Orderbook>{
    @Override
    public Orderbook mapRow(Row row, int i) throws DriverException {
        return Orderbook.builder()
            .instrumentId(row.getString("instrument_id"))
            .endedAtDate(row.get("ended_at_date", java.util.Date.class))
            .endedAt(row.get("ended_at", java.util.Date.class))
            .startedAt(row.get("started_at", java.util.Date.class))
            .askMinimumLots(row.getDouble("ask_minimum_lots"))
            .bidMinimumLots(row.getDouble("bid_minimum_lots"))
            .build();
    }
}
