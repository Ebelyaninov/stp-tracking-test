package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioRate;

import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioRateRowMapper;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MasterPortfolioRateDao {
    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioRateRowMapper masterPortfolioRateRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;


    @Step("Поиск записи в master_portfolio_rate по  strategyId")
    @SneakyThrows
    public MasterPortfolioRate getMasterPortfolioRateByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_rate " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, masterPortfolioRateRowMapper, strategyId);
    }
    @Step("Удаление записи  в master_portfolio_rate по strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioRateByStrategyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_portfolio_rate")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }

    @Step("Поиск записи в master_portfolio_rate по  strategyId")
    @SneakyThrows
    public Optional<MasterPortfolioRate> findMasterPortfolioRateByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_rate " +
            "where strategy_id = ? ";
        List<MasterPortfolioRate> result = cqlTemplate.query(query, masterPortfolioRateRowMapper, strategyId);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));

    }
}