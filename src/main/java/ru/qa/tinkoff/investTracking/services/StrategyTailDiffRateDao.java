package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.StrategyTailDiffRate;
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.StrategyTailDiffRateRowMapper;
import ru.qa.tinkoff.investTracking.rowmapper.StrategyTailValueRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StrategyTailDiffRateDao {
    private final CqlTemplate cqlTemplate;
    private final StrategyTailDiffRateRowMapper strategyTailDiffRateRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;

    @Step("Поиск в cassandra записи по объему хвоста стратегии по strategyId")
    @SneakyThrows
    public StrategyTailDiffRate getStrategyTailDiffRateByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.strategy_tail_diff_rate " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, strategyTailDiffRateRowMapper, strategyId);
    }

    @Step("Удаление в cassandra записи по объему хвоста стратегии по strategyId")
    @SneakyThrows
    public void deleteStrategyTailDiffRateByStrategyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("strategy_tail_diff_rate")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }

    @Step("Добавляем запись в strategy_tail_value")
    @SneakyThrows
    public void insertIntoStrategyTailDiffRate(StrategyTailDiffRate strategyTailDiffRate) {
        String query = "insert into invest_tracking.strategy_tail_diff_rate (strategy_id, cut, values) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(strategyTailDiffRate.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query, strategyTailDiffRate.getStrategyId(), timestamp,
            strategyTailDiffRate.getValues());
    }
}
