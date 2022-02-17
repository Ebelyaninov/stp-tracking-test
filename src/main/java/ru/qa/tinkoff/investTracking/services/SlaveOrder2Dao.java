package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.rowmapper.SlaveOrder2RowMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SlaveOrder2Dao {
    private final CqlTemplate cqlTemplate;
    private final SlaveOrder2RowMapper slaveOrder2RowMapper;

    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public SlaveOrder2 getSlaveOrder2(String contractId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "order by created_at DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId);
    }
    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public SlaveOrder2 getSlaveOrder2CreateAt(String contractId, Date createAt) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            " and created_at >= ? " +
            "order by created_at ASC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, createAt);
    }



    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public Optional<SlaveOrder2> findSlaveOrder2(String contractId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "order by created_at DESC " +
            "limit 1";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));
    }


    @Step("Создаем запись о выставленной заявке в slave_order_2")
    public void insertIntoSlaveOrder2(String contractId, OffsetDateTime createAt, UUID strategyId, int version, int attemptsCount,
                                                       int action, String classCode,BigDecimal filledQuantity, UUID idempotencyKey, UUID id, BigDecimal price,
                                                       BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order_2")
            .value("contract_id", contractId)
            .value("created_at", Date.from(createAt.toInstant()))
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("filled_quantity", filledQuantity)
            .value("id", id)
            .value("idempotency_key", idempotencyKey)
            .value("price", price)
            .value("quantity", quantity)
            .value("state", state)
            .value("ticker", ticker)
            .value("trading_clearing_account",tradingClearingAccount);
        cqlTemplate.execute(insertQueryBuilder);
    }

    public void insertIntoSlaveOrder2WithFilledQuantity(String contractId, UUID strategyId, int version, int attemptsCount,
                                                        int action, String classCode,BigDecimal filledQuantity, UUID idempotencyKey, UUID id, BigDecimal price,
                                                        BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order_2")
            .value("contract_id", contractId)
            .value("created_at", Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("filled_quantity", filledQuantity)
            .value("id", id)
            .value("idempotency_key", idempotencyKey)
            .value("price", price)
            .value("quantity", quantity)
            .value("state", state)
            .value("ticker", ticker)
            .value("trading_clearing_account",tradingClearingAccount);

        cqlTemplate.execute(insertQueryBuilder);
    }



    @Step("Удаляем запись о выставленной заявке в slave_order_2")
    public void deleteSlaveOrder2(String contract) {
        Delete.Where delete = QueryBuilder.delete()
            .from("slave_order_2")
            .where(QueryBuilder.eq("contract_id", contract));
        cqlTemplate.execute(delete);
    }


    public Optional<SlaveOrder2> getLatestSlaveOrder2 (String contractId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "WHERE contract_id = :contract_id " +
            "ORDER BY created_at DESC " +
            "LIMIT 1";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId);
        return Optional.of(result.get(0));
    }

    @Step("Получаем запись о выставленной заявке в slave_order_2 по стратегии")
    public SlaveOrder2 getSlaveOrder2ByStrategy(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "and strategy_id = ? " +
            "LIMIT 1 " +
            "ALLOW FILTERING";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, strategyId);
    }

    @Step("Получаем список записей о выставленной заявке в slave_order_2 по стратегии")
    public List<SlaveOrder2> getSlaveOrder2WithStrategy(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "and strategy_id = ? " +
            "ALLOW FILTERING";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId, strategyId);
        return result;
    }

}
