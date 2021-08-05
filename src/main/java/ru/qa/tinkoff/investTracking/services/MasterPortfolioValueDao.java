package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.rowmapper.LongOnlyValueMapper;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioValueRowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterPortfolioValueDao {

    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioValueRowMapper masterPortfolioValueRowMapper;
    private final LongOnlyValueMapper longOnlyValueMapper;

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public MasterPortfolioValue getMasterPortfolioValueByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_value " +
            "where strategy_id = ? ";
        return cqlTemplate.queryForObject(query, masterPortfolioValueRowMapper, strategyId);
    }

    public List<Pair<LocalDateTime, BigDecimal>> getMasterPortfolioValuesByStrategyId(UUID strategyId, Date start, Date end) {
        var query = "select cut, value " +
            "from invest_tracking.master_portfolio_value " +
            "where strategy_id = ? " +
            "and cut >= ? and cut <= ? ";
        return cqlTemplate.query(query,
            (row, rownum) -> {
                Date cut = row.get("cut", Date.class);
                BigDecimal value = row.get("value", BigDecimal.class);
                return Pair.of(convertToLocalDateViaInstant(cut), value);
            }, strategyId, start, end);
    }

    public LocalDateTime convertToLocalDateViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public Optional<MasterPortfolioValue> findMasterPortfolioValueByStrategyId(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_value " +
            "where strategy_id = ? ";
        List<MasterPortfolioValue> result = cqlTemplate.query(query, masterPortfolioValueRowMapper, strategyId);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));

    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    @SuppressWarnings("ConstantConditions")
    public long count(UUID strategyId) {
        String query = "select count(*) " +
            "from invest_tracking.master_portfolio_value " +
            "where strategy_id = ? ";
        return cqlTemplate.query(query, longOnlyValueMapper, strategyId);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioValueByStrategyId(UUID strategyId) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_portfolio_value")
            .where(QueryBuilder.eq("strategy_id", strategyId));
        cqlTemplate.execute(delete);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void deleteMasterPortfolioValueByStrategyIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            log.error("Удаление стратегий не выполняется - пустой список идентификаторов стратегий");
        }
        for (int i = 0; i < ids.size(); i++) {
            Delete.Where delete = QueryBuilder.delete()
                .from("master_portfolio_value")
                .where(QueryBuilder.eq("strategy_id", ids.get(i)));
            cqlTemplate.execute(delete);
        }

    }

    @Step("Добавляем запись в master_portfolio_value")
    @SneakyThrows
    public void insertIntoMasterPortfolioValue(MasterPortfolioValue masterPortfolioValue) {
        String query = "insert into invest_tracking.master_portfolio_value (strategy_id, cut, value) " +
            "values (?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query,
            masterPortfolioValue.getStrategyId(),
            timestamp,
            masterPortfolioValue.getValue());
    }
}
