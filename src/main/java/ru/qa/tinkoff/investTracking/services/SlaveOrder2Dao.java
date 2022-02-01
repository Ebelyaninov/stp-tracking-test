package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.rowmapper.SlaveOrder2RowMapper;
import ru.qa.tinkoff.investTracking.rowmapper.SlaveOrderRowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    public SlaveOrder2 getSlaveOrder2(String contractId) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "order by created_at DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId);
    }

    public SlaveOrder2 getSlaveOrder2State(String contractId, Date createAt, Byte state) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            " and created_at = ? " +
            " and state = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, createAt);
    }



    public SlaveOrder2 getSlaveOrder2WithVersionAndAttemps(String contractId, Date createAt, Integer version, Byte attemptsCount) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "  and created_at = ? " +
            "and version = ?  and attempts_count = ?";
        return cqlTemplate.queryForObject(query, slaveOrder2RowMapper, contractId, createAt, version, attemptsCount);
    }

    public List<SlaveOrder2> findSlaveOrder2Limit(String contractId, Date createAt, int limit) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "  and created_at = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit ?";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId, createAt, limit);
        return result;
    }

    public Optional<SlaveOrder2> findSlaveOrder2(String contractId, Date createAt) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "  and created_at = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit 1";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId, createAt);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));
    }

    public Optional<SlaveOrder2> findSlaveOrder2WithVersionAndAttemps(String contractId, Date createAt, Integer version, Byte attemptsCount) {
        String query = "select * " +
            "from invest_tracking.slave_order_2 " +
            "where contract_id = ? " +
            "  and created_at = ? " +
            "and version = ?  and attempts_count = ?";
        List<SlaveOrder2> result = cqlTemplate.query(query, slaveOrder2RowMapper, contractId, createAt, version, attemptsCount);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));
    }


    @Step("Инсерт заявок в cassandra по contractId и strategyId")
    @SneakyThrows
    public void  insertSlaveOrder2(SlaveOrder2 slaveOrder2) {
        String query = "insert into invest_tracking.slave_order_2 (contract_id, strategy_id, version, attempts_count," +
            " action, class_code, created_at, filled_quantity, id, idempotency_key, price, quantity," +
            " state, ticker, trading_clearing_account) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(slaveOrder2.getCreateAt().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query,
            slaveOrder2.getContractId(),
            slaveOrder2.getStrategyId(),
            slaveOrder2.getVersion(),
            slaveOrder2.getAttemptsCount(),
            slaveOrder2.getAction(),
            slaveOrder2.getClassCode(),
            timestamp,
            slaveOrder2.getFilledQuantity(),
            slaveOrder2.getId(),
            slaveOrder2.getIdempotencyKey(),
            slaveOrder2.getPrice(),
            slaveOrder2.getQuantity(),
            slaveOrder2.getState(),
            slaveOrder2.getTicker(),
            slaveOrder2.getTradingClearingAccount()
        );
    }


    public void insertIntoSlaveOrder2(String contractId, UUID strategyId, int version, int attemptsCount,
                                     int action, String classCode, UUID idempotencyKey, UUID id, BigDecimal price,
                                     BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount, BigDecimal filleduQantity) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order_2")
            .value("contract_id", contractId)
            .value("created_at", Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("id", id)
            .value("idempotency_key", idempotencyKey)
            .value("price", price)
            .value("quantity", quantity)
            .value("state", state)
            .value("ticker", ticker)
            .value("trading_clearing_account",tradingClearingAccount)
            .value("filled_quantity", filleduQantity);
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


    public void insertIntoSlaveOrder2WithFilledQuantityCrTime(String contractId, UUID strategyId, int version, int attemptsCount,
                                                             int action, String classCode,BigDecimal filledQuantity, UUID idempotencyKey, UUID id, BigDecimal price,
                                                             BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order_2")
            .value("contract_id", contractId)
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
            .value("created_at", Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
            ;

        cqlTemplate.execute(insertQueryBuilder);
    }

    public void deleteSlaveOrder2(String contract) {
        Delete.Where delete = QueryBuilder.delete()
            .from("slave_order_2")
            .where(QueryBuilder.eq("contract_id", contract));
//            .and(QueryBuilder.eq("created_at", createAt));
        cqlTemplate.execute(delete);
    }
}
