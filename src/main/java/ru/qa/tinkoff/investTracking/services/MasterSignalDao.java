package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.PositionId;
import ru.qa.tinkoff.investTracking.rowmapper.MasterSignalRowMapper;
import ru.qa.tinkoff.investTracking.rowmapper.PositionIdRowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MasterSignalDao {

    private final CqlTemplate cqlTemplate;
    private final MasterSignalRowMapper masterSignalRowMapper;
    private final PositionIdRowMapper positionIdRowMapper;

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public MasterSignal getMasterSignalByVersion(UUID strategyId, int version) {
        String query = "select * " +
            "from invest_tracking.master_signal " +
            "where strategy_id = ? " +
            "and version = ? " +
            "order by version desc limit 1";
        return cqlTemplate.queryForObject(query, masterSignalRowMapper, strategyId, version);
    }


    @Step("Поиск сигналов в cassandra strategyId")
    @SneakyThrows
    public List<MasterSignal>  getAllMasterSignal(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_signal " +
            "where strategy_id = ? " +
            "order by version desc";
        return cqlTemplate.query(query, masterSignalRowMapper, strategyId);
    }

    @Step("Поиск сигналов по курсору в cassandra strategyId")
    @SneakyThrows
    public List<MasterSignal>  getMasterSignalWithCursor(UUID strategyId, int cursor) {
        String query = "select * " +
            "from invest_tracking.master_signal " +
            "where strategy_id = ? " +
            "and version < ? " +
            "order by version desc";
        return cqlTemplate.query(query, masterSignalRowMapper, strategyId, cursor);
    }

    @Step("Поиск сигналов по курсору и лимиту в cassandra strategyId")
    @SneakyThrows
    public List<MasterSignal>  getMasterSignalWithCursorAndLimit(UUID strategyId, int cursor, int limit) {
        String query = "select * " +
            "from invest_tracking.master_signal " +
            "where strategy_id = ? " +
            "and version < ? " +
            "order by version desc limit ?";
        return cqlTemplate.query(query, masterSignalRowMapper, strategyId, cursor, limit);
    }



    @Step("Поиск сигналов в cassandra по contractId и strategyId")
    @SneakyThrows
    public Integer countCreatedAtMasterSignal(UUID strategyId, Date createdAt) {
        var query = "select count(*) from invest_tracking.created_at_master_signal " +
            "where strategy_id = ? and created_at <= ?";
        return cqlTemplate.queryForObject(query, Long.class, strategyId, createdAt).intValue();
    }


    public List<PositionId> getMasterSignalPositionIdsByPeriod(UUID strategy, Date start, Date end) {
        var query = "select ticker, trading_clearing_account from created_at_master_signal where strategy_id = ? " +
            "and created_at >= ? and created_at <= ?";
        return cqlTemplate.query(query, positionIdRowMapper, strategy, start, end);
    }

    public List<LocalDate> getUniqMasterSignalDaysByPeriod(UUID strategyId, Date start, Date end) {
        var query = "select toDate(created_at) as day " +
            "from created_at_master_signal where strategy_id = ? and created_at >= ? and created_at <= ?";
        return cqlTemplate.queryForList(query, LocalDate.class, strategyId, start, end);
    }


    @Step("Поиск сигналов в cassandra по contractId и strategyId")
    @SneakyThrows
    public void  insertIntoMasterSignal(MasterSignal masterSignal) {
        String query = "insert into invest_tracking.master_signal (strategy_id, version, ticker," +
            " trading_clearing_account, action, quantity, tail_order_quantity, price, created_at, state, dynamic_limit_quantity) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime ldt = LocalDateTime.ofInstant(masterSignal.getCreatedAt().toInstant(), ZoneId.systemDefault());
        Timestamp timestamp = Timestamp.valueOf(ldt);
        cqlTemplate.execute(query,
            masterSignal.getStrategyId(),
            masterSignal.getVersion(),
            masterSignal.getTicker(),
            masterSignal.getTradingClearingAccount(),
            masterSignal.getAction(),
            masterSignal.getQuantity(),
            masterSignal.getTailOrderQuantity(),
            masterSignal.getPrice(),
            timestamp,
            masterSignal.getState(),
            masterSignal.getDynamicLimitQuantity()
        );
    }

    @Step("Поиск сигналов в cassandra по contractId и strategyId")
    @SneakyThrows
    public boolean updateStateMasterSignal(UUID strategyId, int commandVersion, byte state) {
        String query = "update invest_tracking.master_signal set state = ? " +
            "where strategy_id = ? and version = ?";
        return cqlTemplate.execute(query, state, strategyId, commandVersion);
    }

    public void deleteMasterSignal(UUID strategy, int version ) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_signal")
            .where(QueryBuilder.eq("strategy_id", strategy))
            .and(QueryBuilder.eq("version", version));
        cqlTemplate.execute(delete);
    }

    public void deleteMasterSignalByStrategy(UUID strategy ) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_signal")
            .where(QueryBuilder.eq("strategy_id", strategy));
        cqlTemplate.execute(delete);
    }
}
