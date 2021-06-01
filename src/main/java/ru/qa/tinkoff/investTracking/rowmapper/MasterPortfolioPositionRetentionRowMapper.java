package ru.qa.tinkoff.investTracking.rowmapper;


import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioPositionRetention;

@Component
public class MasterPortfolioPositionRetentionRowMapper implements RowMapper<MasterPortfolioPositionRetention> {

    @Override
    public MasterPortfolioPositionRetention mapRow(Row row, int i) throws DriverException {
        return MasterPortfolioPositionRetention.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .value(row.getString("value"))
            .build();
    }
}