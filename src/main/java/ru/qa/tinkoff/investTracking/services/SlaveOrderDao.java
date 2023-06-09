package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.rowmapper.SlaveOrderRowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SlaveOrderDao {

    private final CqlTemplate cqlTemplate;
    private final SlaveOrderRowMapper slaveOrderRowMapper;

    public SlaveOrder getSlaveOrder(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_order " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slaveOrderRowMapper, contractId, strategyId);
    }

    public SlaveOrder getSlaveOrderState(String contractId, UUID strategyId, Byte state) {
        String query = "select * " +
            "from invest_tracking.slave_order " +
            "where contract_id = ? " +
            " and strategy_id = ? " +
            " and state = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slaveOrderRowMapper, contractId, strategyId);
    }



    public SlaveOrder getSlaveOrderWithVersionAndAttemps(String contractId, UUID strategyId, Integer version, Byte attemptsCount) {
        String query = "select * " +
            "from invest_tracking.slave_order " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "and version = ?  and attempts_count = ?";
        return cqlTemplate.queryForObject(query, slaveOrderRowMapper, contractId, strategyId, version, attemptsCount);
    }

    public List<SlaveOrder> findSlaveOrderLimit(String contractId, UUID strategyId, int limit) {
        String query = "select * " +
            "from invest_tracking.slave_order " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit ?";
        List<SlaveOrder> result = cqlTemplate.query(query, slaveOrderRowMapper, contractId, strategyId, limit);
        return result;
    }

    public Optional<SlaveOrder> findSlaveOrder(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_order " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version DESC, attempts_count DESC " +
            "limit 1";
        List<SlaveOrder> result = cqlTemplate.query(query, slaveOrderRowMapper, contractId, strategyId);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));
    }

    public Optional<SlaveOrder> findSlaveOrderWithVersionAndAttemps(String contractId, UUID strategyId, Integer version, Byte attemptsCount) {
        String query = "select * " +
            "from invest_tracking.slave_order " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "and version = ?  and attempts_count = ?";
        List<SlaveOrder> result = cqlTemplate.query(query, slaveOrderRowMapper, contractId, strategyId,version, attemptsCount);
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
    public void  insertSlaveOrder(SlaveOrder slaveOrder) {
        String query = "insert into invest_tracking.slave_order (contract_id, strategy_id, version, attempts_count," +
            " action, class_code, created_at, filled_quantity, idempotency_key, price, quantity," +
        " state, ticker, trading_clearing_account) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(slaveOrder.getCreateAt().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query,
            slaveOrder.getContractId(),
            slaveOrder.getStrategyId(),
            slaveOrder.getVersion(),
            slaveOrder.getAttemptsCount(),
            slaveOrder.getAction(),
            slaveOrder.getClassCode(),
            timestamp,
            slaveOrder.getFilledQuantity(),
            slaveOrder.getIdempotencyKey(),
            slaveOrder.getPrice(),
            slaveOrder.getQuantity(),
            slaveOrder.getState(),
            slaveOrder.getTicker(),
            slaveOrder.getTradingClearingAccount()
        );
    }


    public void insertIntoSlaveOrder(String contractId, UUID strategyId, int version, int attemptsCount,
                                     int action, String classCode, UUID idempotencyKey, BigDecimal price,
                                     BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount, BigDecimal filleduQantity) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("idempotency_key", idempotencyKey)
            .value("price", price)
            .value("quantity", quantity)
            .value("state", state)
            .value("ticker", ticker)
            .value("trading_clearing_account",tradingClearingAccount)
            .value("filled_quantity", filleduQantity);
        cqlTemplate.execute(insertQueryBuilder);
    }


    public void insertIntoSlaveOrderWithFilledQuantity(String contractId, UUID strategyId, int version, int attemptsCount,
                                     int action, String classCode,BigDecimal filledQuantity, UUID idempotencyKey, BigDecimal price,
                                     BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("filled_quantity", filledQuantity)
            .value("idempotency_key", idempotencyKey)
            .value("price", price)
            .value("quantity", quantity)
            .value("state", state)
            .value("ticker", ticker)
            .value("trading_clearing_account",tradingClearingAccount);

        cqlTemplate.execute(insertQueryBuilder);
    }


    public void insertIntoSlaveOrderWithFilledQuantityCrTime(String contractId, UUID strategyId, int version, int attemptsCount,
                                                       int action, String classCode,BigDecimal filledQuantity, UUID idempotencyKey, BigDecimal price,
                                                       BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {
        Insert insertQueryBuilder = QueryBuilder.insertInto("slave_order")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("attempts_count", attemptsCount)
            .value("action", action)
            .value("class_code", classCode)
            .value("filled_quantity", filledQuantity)
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

    public void deleteSlaveOrder(String contract, UUID strategy) {
        Delete.Where delete = QueryBuilder.delete()
            .from("slave_order")
            .where(QueryBuilder.eq("contract_id", contract))
            .and(QueryBuilder.eq("strategy_id", strategy));
        cqlTemplate.execute(delete);
    }

}
