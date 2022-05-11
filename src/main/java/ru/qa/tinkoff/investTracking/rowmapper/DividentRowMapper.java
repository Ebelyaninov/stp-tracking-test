package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.Dividend;

import java.io.IOException;

@Component
@RequiredArgsConstructor
 public class DividentRowMapper implements RowMapper<Dividend> {

    private final ObjectMapper objectMapper;
    @Override
    @SneakyThrows
    public Dividend mapRow(Row row, int rowNum) throws DriverException {
        return Dividend.builder()
            .contractId(row.getString("contract_id"))
            .strategyId(row.getUUID("strategy_id"))
            .id(row.getLong("id"))
            .context(objectMapper.readValue(row.getString("context"), Dividend.Context.class))
            .build();
    }
}
