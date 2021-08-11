package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;
import ru.qa.tinkoff.investTracking.entities.SignalsCount;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.SignalsCountRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SignalsCountDao {
    private final CqlTemplate cqlTemplate;
    private final SignalsCountRowMapper signalsCountRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;


    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public SignalsCount getSignalsCountByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.signals_count " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, signalsCountRowMapper, strategyId);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    @SuppressWarnings("ConstantConditions")
    public long count(UUID strategyId) {
        String query = "select count(*) " +
            "from invest_tracking.signals_count " +
            "where strategy_id = ? ";
        return cqlTemplate.query(query, longOnlyValueMapper, strategyId);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteSignalsCountByStratedyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("signals_count")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }

    @Step("Добавляем запись в signals_count")
    @SneakyThrows
    public void insertIntoSignalsCount(SignalsCount signalsCount) {
        String query = "insert into invest_tracking.signals_count (strategy_id, cut, value) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(signalsCount.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query, signalsCount.getStrategyId(), timestamp,
            signalsCount.getValue());
    }
}
