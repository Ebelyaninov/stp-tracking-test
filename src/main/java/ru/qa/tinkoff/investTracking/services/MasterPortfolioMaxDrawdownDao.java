package ru.qa.tinkoff.investTracking.services;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioMaxDrawdownRowMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MasterPortfolioMaxDrawdownDao {
    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioMaxDrawdownRowMapper masterPortfolioMaxDrawdownRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public MasterPortfolioMaxDrawdown getMasterPortfolioMaxDrawdownByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_max_drawdown " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, masterPortfolioMaxDrawdownRowMapper, strategyId);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioMaxDrawdownByStrategyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_portfolio_max_drawdown")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }
}
