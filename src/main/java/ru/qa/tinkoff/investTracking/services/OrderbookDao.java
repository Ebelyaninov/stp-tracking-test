package ru.qa.tinkoff.investTracking.services;

import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.Orderbook;
import ru.qa.tinkoff.investTracking.entities.ResultFee;
import ru.qa.tinkoff.investTracking.rowmapper.OrderbookRowMapper;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderbookDao {
    private final CqlTemplate cqlTemplate;
    private final OrderbookRowMapper orderbookRowMapper;

    public List<Orderbook> findWindowsInstrumentIdEndedAtDate(String InstrumentId, String endedAtDate) {
        String query = "select * from orderbook where instrument_id = ?  and ended_at_date =? order by ended_at desc";
        List<Orderbook> result = cqlTemplate.query(query, orderbookRowMapper, InstrumentId, endedAtDate);
        return  result;
    }

}
