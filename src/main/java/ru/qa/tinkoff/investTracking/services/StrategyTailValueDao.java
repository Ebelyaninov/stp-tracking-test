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
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioMaxDrawdownRowMapper;
import ru.qa.tinkoff.investTracking.rowmapper.StrategyTailValueRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StrategyTailValueDao {
    private final CqlTemplate cqlTemplate;
    private final StrategyTailValueRowMapper strategyTailValueRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;

    @Step("Поиск в cassandra записи по объему хвоста стратегии по strategyId")
    @SneakyThrows
    public StrategyTailValue getStrategyTailValueByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.strategy_tail_value " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, strategyTailValueRowMapper, strategyId);
    }

    @Step("Удаление в cassandra записи по объему хвоста стратегии по strategyId")
    @SneakyThrows
    public void deleteStrategyTailValueByStrategyId(UUID strategyId) {
        Statement delete = QueryBuilder.delete()
            .from("strategy_tail_value")
            .where(QueryBuilder.eq("strategy_id", strategyId))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(delete);
    }

    @Step("Добавляем запись в strategy_tail_value")
    @SneakyThrows
    public void insertIntoStrategyTailValue(StrategyTailValue strategyTailValue) {
        String query = "insert into invest_tracking.strategy_tail_value (strategy_id, cut, value) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(strategyTailValue.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        executeCql(query, ResultSet::wasApplied, strategyTailValue.getStrategyId(), timestamp,
            strategyTailValue.getValue());
//        cqlTemplate.execute(query, strategyTailValue.getStrategyId(), timestamp,
//            strategyTailValue.getValue());
    }

    @Nullable
    private <T> T executeCql(String cql, ResultSetExtractor<T> resultSetExtractor, Object... args) {
        return cqlTemplate.query(
            new ConsistencyLevelCreator(new SimplePreparedStatementCreator(cql), ConsistencyLevel.EACH_QUORUM),
            new ArgumentPreparedStatementBinder(args),
            resultSetExtractor);
    }
}
