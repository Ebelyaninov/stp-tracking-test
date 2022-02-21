package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.Context;
import ru.qa.tinkoff.investTracking.entities.ResultFee;
@Component
@RequiredArgsConstructor
public class ResultFeeRowMapper implements RowMapper<ResultFee> {
    @Qualifier("contextMapper")
    private final ObjectMapper contextMapper;

    @SneakyThrows
    @Override
    public ResultFee mapRow(Row row, int rowNum) throws DriverException {

        Context context = contextMapper.readValue(row.getString("context"), Context.class);
        return ResultFee.builder()
            .contractId(row.getString("contract_id"))
            .strategyId(row.getUUID("strategy_id"))
            .version(row.getInt("version"))
            .settlementPeriodStartedAt(row.get("settlement_period_started_at", java.util.Date.class))
            .context(context)
            .highWaterMark(row.getDecimal("high_water_mark"))
            .settlementPeriodEndedAt(row.get("settlement_period_ended_at", java.util.Date.class))
            .createdAt(row.get("created_at", java.util.Date.class))
            .build();
    }
}
