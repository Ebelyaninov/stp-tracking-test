package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioTopPositionsRowMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MasterPortfolioTopPositionsDao {

    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioTopPositionsRowMapper masterPortfolioTopPositionsRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;

    @Step("Поиск  топ-позиций виртуального портфеля по strategy_id и cut")
    @SneakyThrows
    public MasterPortfolioTopPositions getMasterPortfolioTopPositionsByCut(UUID strategyId, String cut) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_top_positions " +
            "where strategy_id = ? " +
            "and cut = ? ";
        return cqlTemplate.queryForObject(query, masterPortfolioTopPositionsRowMapper, strategyId, cut);
    }



    @Step("Поиск  топ-позиций виртуального портфеля по strategy_id и cut")
    @SneakyThrows
    public MasterPortfolioTopPositions getMasterPortfolioTopPositions(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_top_positions " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, masterPortfolioTopPositionsRowMapper, strategyId);
    }


    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioTopPositionsByStrategyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_portfolio_top_positions")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }

    @Step("Количество записей по strategyId в master_portfolio_top_positions")
    @SneakyThrows
    @SuppressWarnings("ConstantConditions")
    public long count(UUID strategyId) {
        String query = "select count(*) " +
            "from invest_tracking.master_portfolio_top_positions " +
            "where strategy_id = ? ";
        return cqlTemplate.query(query, longOnlyValueMapper, strategyId);
    }


}
