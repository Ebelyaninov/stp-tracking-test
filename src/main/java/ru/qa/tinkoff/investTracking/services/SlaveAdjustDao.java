package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.ResultFee;
import ru.qa.tinkoff.investTracking.entities.SlaveAdjust;
import ru.qa.tinkoff.investTracking.rowmapper.SlaveAdjustRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class SlaveAdjustDao {
    private final CqlTemplate cqlTemplate;
    private final SlaveAdjustRowMapper slaveAdjustRowMapper;


    @Step("Поиск завода в cassandra по contractId и strategyId")
    public List<SlaveAdjust> getSlaveAdjustByStrategyIdAndContract(String contractId, UUID strategyId) {
        String query  = "SELECT * FROM invest_tracking.slave_adjust WHERE " +
            "contract_id = ? AND strategy_id = ? ";
        return cqlTemplate.query(query, slaveAdjustRowMapper,contractId, strategyId);
    }

    @Step("Поиск завода в cassandra по contractId и strategyId")
    public List<SlaveAdjust> getSlaveAdjustByPeriod(String contractId, UUID strategyId, Date from, Date to) {
        String query  = "SELECT * FROM invest_tracking.slave_adjust WHERE " +
            "contract_id = ? AND strategy_id = ?  AND created_at >= ? AND created_at < ?";
        return cqlTemplate.query(query, slaveAdjustRowMapper,contractId, strategyId, from, to);
    }


    @Step("Добавляем запись в signals_count")
    @SneakyThrows
    public void insertIntoSlaveAdjust(SlaveAdjust slaveAdjust) {
        String query = "insert into invest_tracking.slave_adjust(contract_id, strategy_id, created_at, operation_id, quantity, currency, deleted, changed_at)" +
            " VALUES(?, ?, ?, ?, ?, ?, ?, ?) ";
        LocalDateTime ldt = LocalDateTime.ofInstant(slaveAdjust.getCreatedAt().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query,
            slaveAdjust.getContractId(),
            slaveAdjust.getStrategyId(),
            timestamp,
            slaveAdjust.getOperationId(),
            slaveAdjust.getQuantity(),
            slaveAdjust.getCurrency(),
            slaveAdjust.getDeleted(),
            timestamp);
    }


    public void deleteSlaveAdjustByStrategyAndContract(String contractId, UUID strategyId ) {
        Delete.Where delete = QueryBuilder.delete()
            .from("slave_adjust")
            .where(QueryBuilder.eq("strategy_id", strategyId))
            .and(QueryBuilder.eq("contract_id", contractId));
        cqlTemplate.execute(delete);
    }
}
