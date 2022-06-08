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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioPositionRetention;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioPositionRetentionRowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class MasterPortfolioPositionRetentionDao {

    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioPositionRetentionRowMapper masterPortfolioPositionRetentionRowMapper;

    @Step("Поиск value из master position retention в cassandra по strategyId")
    @SneakyThrows
    public MasterPortfolioPositionRetention getMasterPortfolioPositionRetention(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_position_retention " +
            "where strategy_id = ? " +
            "limit 1";
        return cqlTemplate.queryForObject(query, masterPortfolioPositionRetentionRowMapper, strategyId);
    }

    @Step("Поиск value из master position retention в cassandra по strategyId и cut")
    @SneakyThrows
    public MasterPortfolioPositionRetention getMasterPortfolioPositionRetention(UUID strategyId, Date cut) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_position_retention " +
            "where strategy_id = ? " +
            "  and cut = ? " +
            "limit 1";
        return cqlTemplate.queryForObject(query, masterPortfolioPositionRetentionRowMapper, strategyId, cut);
    }

    public void deleteMasterPortfolioPositionRetention(UUID strategy) {
        Statement delete = QueryBuilder.delete()
            .from("master_portfolio_position_retention")
            .where(QueryBuilder.eq("strategy_id", strategy))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(delete);
    }

    @Step("Добавляем запись в master_portfolio_position_retention")
    @SneakyThrows
    public void insertIntoMasterPortfolioPositionRetention(MasterPortfolioPositionRetention masterPortfolioPositionRetention) {
        String query = "insert into invest_tracking.master_portfolio_position_retention (strategy_id, cut, value) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(masterPortfolioPositionRetention.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
//        cqlTemplate.execute(query, masterPortfolioPositionRetention.getStrategyId(), timestamp,
//            masterPortfolioPositionRetention.getValue());
        executeCql(query, ResultSet::wasApplied, masterPortfolioPositionRetention.getStrategyId(), timestamp,
            masterPortfolioPositionRetention.getValue());
    }

    @Step("Поиск списка записей в таблице master_portfolio_position_retention")
    @SneakyThrows
    public List<MasterPortfolioPositionRetention> getListMasterPortfolioPositionRetention(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_position_retention " +
            "where strategy_id = ? ";
         List<MasterPortfolioPositionRetention> result = cqlTemplate.query(query, masterPortfolioPositionRetentionRowMapper, strategyId);
        return result;
    }
    @Nullable
    private <T> T executeCql(String cql, ResultSetExtractor<T> resultSetExtractor, Object... args) {
        return cqlTemplate.query(
            new ConsistencyLevelCreator(new SimplePreparedStatementCreator(cql), ConsistencyLevel.EACH_QUORUM),
            new ArgumentPreparedStatementBinder(args),
            resultSetExtractor);
    }

}
