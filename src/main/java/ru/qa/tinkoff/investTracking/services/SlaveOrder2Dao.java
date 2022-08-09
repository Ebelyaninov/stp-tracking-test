package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.*;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
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
@Slf4j
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
        log.info("{}, {}", contractId, createAt);
        SlaveOrder2 slaveOrder2 = cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, createAt);
        log.info("{}", slaveOrder2);
        return slaveOrder2;
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
                                                       int action, String classCode, Integer comparedToMasterVersion, BigDecimal filledQuantity, UUID idempotencyKey, UUID id, BigDecimal price,
                                                       BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount, UUID positionId) {
        Statement insertQueryBuilder = QueryBuilder.insertInto("slave_order_2")
            .value("contract_id", contractId)
            .value("created_at", Date.from(createAt.toInstant()))
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("compared_to_master_version", comparedToMasterVersion)
            .value("filled_quantity", filledQuantity)
            .value("id", id)
            .value("idempotency_key", idempotencyKey)
            .value("price", price)
            .value("quantity", quantity)
            .value("state", state)
            .value("ticker", ticker)
            .value("trading_clearing_account",tradingClearingAccount)
            .value("position_id", positionId)
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(insertQueryBuilder);
    }

    public void insertIntoSlaveOrder2WithFilledQuantity(String contractId, UUID strategyId, int version, int attemptsCount,
                                                        int action, String classCode,BigDecimal filledQuantity, UUID idempotencyKey, UUID id, BigDecimal price,
                                                        BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount, UUID positionId) {
        Statement insertQueryBuilder = QueryBuilder.insertInto("slave_order_2")
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
            .value("trading_clearing_account",tradingClearingAccount)
            .value("position_id", positionId)
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(insertQueryBuilder);
    }



    @Step("Удаляем запись о выставленной заявке в slave_order_2")
    public void deleteSlaveOrder2(String contract) {
        Statement delete = QueryBuilder.delete()
            .from("slave_order_2")
            .where(QueryBuilder.eq("contract_id", contract))
        .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
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


    public List<SlaveOrder2> findSlaveOrder2Limit(String contractId, int limit) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "order by created_at  DESC " +
            "limit ?";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId, limit);
        return result;
    }


    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public List<SlaveOrder2> getAllSlaveOrder2ByContract(String contractId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "order by created_at DESC ";
         List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId);
        return result;
    }

    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public List<SlaveOrder2> getAllSlaveOrder2ByContractAndOrderCreatedAtAsc(String contractId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "order by created_at ASC ";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId);
        return result;
    }

    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public List<SlaveOrder2> getSlaveOrders2WithStrategy(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "and strategy_id = ?" +
            "order by created_at DESC " +
            "ALLOW FILTERING";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId, strategyId);
        return result;
    }

    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public SlaveOrder2 getSlaveOrder2ByStrategy(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "and strategy_id = ?" +
            "order by created_at DESC " +
            "LIMIT 1 " +
            "ALLOW FILTERING ";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, strategyId);
    }

    @Step("Проверяем запись о выставленной заявке в slave_order_2")
    public SlaveOrder2 getSlaveOrderByVersion(String contractId, Integer version) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "and version = ?" +
            "order by created_at ASC " +
            "LIMIT 1 " +
            "ALLOW FILTERING ";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, version);
    }
}
