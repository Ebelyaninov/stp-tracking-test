package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.cassandra.core.cql.ArgumentPreparedStatementBinder;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.data.cassandra.core.cql.ResultSetExtractor;
import org.springframework.data.cassandra.core.cql.SimplePreparedStatementCreator;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioPositionRetention;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioTopPositionsRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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


    @Step("Получение списка топ-позиций виртуального портфеля по strategy_id")
    @SneakyThrows
    public List<MasterPortfolioTopPositions> getMasterPortfolioTopPositionsList(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_top_positions " +
            "where strategy_id = ? ";
        List <MasterPortfolioTopPositions> result = cqlTemplate.query(query, masterPortfolioTopPositionsRowMapper, strategyId);
        return result;
    }


    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioTopPositionsByStrategyId(UUID strategyId) {
        Statement delete = QueryBuilder.delete()
            .from("master_portfolio_top_positions")
            .where(QueryBuilder.eq("strategy_id", strategyId))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
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


    @Step("Добавляем запись в master_portfolio_top_positions")
    @SneakyThrows
    public void insertIntoMasterPortfolioTopPositions(MasterPortfolioTopPositions masterPortfolioTopPositions) {
        String query = "insert into invest_tracking.master_portfolio_top_positions (strategy_id, cut, positions) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(masterPortfolioTopPositions.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        executeCql(query, ResultSet::wasApplied, masterPortfolioTopPositions.getStrategyId(), timestamp,
            masterPortfolioTopPositions.getPositions());
//        cqlTemplate.execute(query, masterPortfolioTopPositions.getStrategyId(), timestamp,
//            masterPortfolioTopPositions.getPositions());
    }

    @Nullable
    private <T> T executeCql(String cql, ResultSetExtractor<T> resultSetExtractor, Object... args) {
        return cqlTemplate.query(
            new ConsistencyLevelCreator(new SimplePreparedStatementCreator(cql), ConsistencyLevel.EACH_QUORUM),
            new ArgumentPreparedStatementBinder(args),
            resultSetExtractor);
    }


}
