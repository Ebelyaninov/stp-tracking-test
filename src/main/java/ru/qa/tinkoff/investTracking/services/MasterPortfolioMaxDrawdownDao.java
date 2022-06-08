package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.cassandra.core.cql.*;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioMaxDrawdownRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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


    @Step("Поиск портфелей в cassandra по strategyId")
    @SneakyThrows
    public List <MasterPortfolioMaxDrawdown> getMasterPortfolioMaxDrawdownList(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_max_drawdown " +
            "where strategy_id = ? ";
        List<MasterPortfolioMaxDrawdown> result = executeCql(query,
            new RowMapperResultSetExtractor<>(masterPortfolioMaxDrawdownRowMapper),
            strategyId);
        return result;
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioMaxDrawdownByStrategyId(UUID strategyId) {
        Statement delete = QueryBuilder.delete()
            .from("master_portfolio_max_drawdown")
            .where(QueryBuilder.eq("strategy_id", strategyId))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(delete);

    }

    @Step("Добавляем запись в master_portfolio_max_drawdown")
    @SneakyThrows
    public void insertIntoMasterPortfolioMaxDrawdown(MasterPortfolioMaxDrawdown masterPortfolioMaxDrawdown) {
        String query = "insert into invest_tracking.master_portfolio_max_drawdown (strategy_id, cut, value) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(masterPortfolioMaxDrawdown.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        executeCql(query, ResultSet::wasApplied, masterPortfolioMaxDrawdown.getStrategyId(), timestamp,
            masterPortfolioMaxDrawdown.getValue());
    }

    @Nullable
    private <T> T executeCql(String cql, ResultSetExtractor<T> resultSetExtractor, Object... args) {
        return cqlTemplate.query(
            new ConsistencyLevelCreator(new SimplePreparedStatementCreator(cql), ConsistencyLevel.EACH_QUORUM),
            new ArgumentPreparedStatementBinder(args),
            resultSetExtractor);
    }

}
