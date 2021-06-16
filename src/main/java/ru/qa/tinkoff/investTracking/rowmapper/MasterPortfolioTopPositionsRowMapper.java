package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.data.cassandra.core.cql.RowMapper;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.entities.TopPosition;

import java.util.List;
import java.util.stream.Collectors;


@Component
public class MasterPortfolioTopPositionsRowMapper implements RowMapper<MasterPortfolioTopPositions> {

    @Override
    public MasterPortfolioTopPositions mapRow(Row row, int i) throws DriverException {
        return MasterPortfolioTopPositions.builder()
            .strategyId(row.getUUID("strategy_id"))
            .cut(row.get("cut", java.util.Date.class))
            .positions(positionsFromUdtValues(row.getList("positions", UDTValue.class)))
            .build();
    }

    private List<TopPosition> positionsFromUdtValues(List<UDTValue> udtValues) {
        return udtValues == null
            ? null
            : udtValues.stream().map(
            udtValue -> TopPosition.builder()
                .ticker(udtValue.getString("ticker"))
                .tradingClearingAccount(udtValue.getString("trading_clearing_account"))
                .signalsCount(udtValue.getInt("signals_count")).build()
        ).collect(Collectors.toList());
    }

}
