package ru.qa.tinkoff.investTracking.rowmapper;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;


@Component
public class MasterPortfolioMaxDrawdownRowMapper implements RowMapper<MasterPortfolioMaxDrawdown> {
    @Override
    public MasterPortfolioMaxDrawdown mapRow(Row row, int i) throws DriverException {
        return MasterPortfolioMaxDrawdown.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .value(row.getDecimal("value"))
            .build();
    }
}
