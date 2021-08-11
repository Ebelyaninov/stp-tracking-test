package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;
import ru.qa.tinkoff.investTracking.entities.SignalFrequency;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.SignalFrequencyRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SignalFrequencyDao {
    private final CqlTemplate cqlTemplate;
    private final SignalFrequencyRowMapper signalFrequencyRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;


    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public SignalFrequency getSignalFrequencyByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.signal_frequency " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, signalFrequencyRowMapper, strategyId);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    @SuppressWarnings("ConstantConditions")
    public long count(UUID strategyId) {
        String query = "select count(*) " +
            "from invest_tracking.signal_frequency " +
            "where strategy_id = ? ";
        return cqlTemplate.query(query, longOnlyValueMapper, strategyId);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteSignalFrequencyByStrategyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("signal_frequency")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }

    @Step("Добавляем запись в signal_frequency")
    @SneakyThrows
    public void insertIntoSignalFrequency(SignalFrequency signalFrequency) {
        String query = "insert into invest_tracking.signal_frequency (strategy_id, cut, count) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(signalFrequency.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query, signalFrequency.getStrategyId(), timestamp,
            signalFrequency.getCount());
    }
}
