package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;

@Component
@RequiredArgsConstructor
public class SlavePortfolioRowMapper implements RowMapper<SlavePortfolio> {

    @Override
    public SlavePortfolio mapRow(Row row, int rowNum) throws DriverException {
        return SlavePortfolio.builder()
            .contractId(row.getString("contract_id"))
            .strategyId(row.getUUID("strategy_id"))
            .version(row.getInt("version"))
            .comparedToMasterVersion(row.getInt("compared_to_master_version"))
            .changedAt(row.get("changed_at", java.util.Date.class))
            .positions(row.getList("positions", SlavePortfolio.Position.class))
            .baseMoneyPosition(row.get("base_money_position", SlavePortfolio.BaseMoneyPosition.class))
            .targetFeeReserveQuantity(row.getDecimal("target_fee_reserve_quantity"))
            .actualFeeReserveQuantity(row.getDecimal("actual_fee_reserve_quantity"))
            .value(row.getDecimal("value"))
            .build();
    }
}
