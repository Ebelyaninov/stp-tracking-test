package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;

@Component
@RequiredArgsConstructor
public class MasterPortfolioRowMapper implements RowMapper<MasterPortfolio> {

    @Override
    public MasterPortfolio mapRow(Row row, int rowNum) throws DriverException {
        return MasterPortfolio.builder()
            .contractId(row.getString("contract_id"))
            .strategyId(row.getUUID("strategy_id"))
            .version(row.getInt("version"))
            .positions(row.getList("positions", MasterPortfolio.Position.class))
            .baseMoneyPosition(row.get("base_money_position", MasterPortfolio.BaseMoneyPosition.class))
            .build();
    }
}
