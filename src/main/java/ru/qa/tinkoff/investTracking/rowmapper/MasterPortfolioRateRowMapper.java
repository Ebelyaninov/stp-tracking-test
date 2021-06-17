package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioRate;

import java.math.BigDecimal;


@Component
@RequiredArgsConstructor
public class MasterPortfolioRateRowMapper implements RowMapper<MasterPortfolioRate> {
    @Override
    public MasterPortfolioRate mapRow(Row row, int rowNum) throws DriverException {
        return MasterPortfolioRate.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .companyToRateMap(row.getMap("companies", String.class, BigDecimal.class))
            .sectorToRateMap(row.getMap("sectors", String.class, BigDecimal.class))
            .typeToRateMap(row.getMap("types", String.class, BigDecimal.class))
            .build();
    }

}