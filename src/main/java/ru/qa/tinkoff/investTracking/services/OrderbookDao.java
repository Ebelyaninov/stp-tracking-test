package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.Orderbook;
import ru.qa.tinkoff.investTracking.rowmapper.OrderbookRowMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderbookDao {
    private final CqlTemplate cqlTemplate;
    private final OrderbookRowMapper orderbookRowMapper;

    public List<Orderbook> findWindowsInstrumentIdEndedAtDate(String InstrumentId, LocalDate endedAtDate) {
        String query = "select * from orderbook where instrument_id = ? and ended_at_date = ? order by ended_at desc";
        List<Orderbook> result = cqlTemplate.query(query, orderbookRowMapper, InstrumentId, endedAtDate);
        return  result;
    }

}
