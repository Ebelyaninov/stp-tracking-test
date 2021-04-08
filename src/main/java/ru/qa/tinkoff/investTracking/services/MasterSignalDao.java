package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.rowmapper.MasterSignalRowMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MasterSignalDao {

    private final CqlTemplate cqlTemplate;
    private final MasterSignalRowMapper masterSignalRowMapper;


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

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public void  insertIntoMasterSignal(MasterSignal masterSignal) {
        String query = "insert into invest_tracking.master_signal (strategy_id, version, ticker," +
            " trading_clearing_account, action, quantity, price, created_at, state) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        cqlTemplate.execute(query,
            masterSignal.getStrategyId(),
            masterSignal.getVersion(),
            masterSignal.getTicker(),
            masterSignal.getTradingClearingAccount(),
            masterSignal.getAction(),
            masterSignal.getQuantity(),
            masterSignal.getPrice(),
            masterSignal.getCreatedAt(),
            masterSignal.getState()
        );
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
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
}
