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
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioRowMapper;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor

public class MasterPortfolioDao {

    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioRowMapper masterPortfolioRowMapper;

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public MasterPortfolio getLatestMasterPortfolio(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version desc " +
            "limit 1";
        return cqlTemplate.queryForObject(query, masterPortfolioRowMapper, contractId, strategyId);

    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public Optional<MasterPortfolio> findLatestMasterPortfolio(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version desc " +
            "limit 1";
        List<MasterPortfolio> result = cqlTemplate.query(query, masterPortfolioRowMapper, contractId, strategyId);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));

    }




    public void insertIntoMasterPortfolio(String contractId, UUID strategyId, int version,
                                          MasterPortfolio.BaseMoneyPosition baseMoneyPosition,
                                          List<MasterPortfolio.Position> positionList) {
        Insert insertQueryBuider = QueryBuilder.insertInto("master_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("base_money_position", baseMoneyPosition)
            .value("positions",positionList);
        cqlTemplate.execute(insertQueryBuider);
    }

    public void insertIntoMasterPortfolioWithChangedAt(String contractId, UUID strategyId, int version,
                                          MasterPortfolio.BaseMoneyPosition baseMoneyPosition,
                                          List<MasterPortfolio.Position> positionList, Date time) {
        Insert insertQueryBuider = QueryBuilder.insertInto("master_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("base_money_position", baseMoneyPosition)
            .value("changed_at", time)
            .value("positions",positionList);
        cqlTemplate.execute(insertQueryBuider);
    }

    public void deleteMasterPortfolio(String contract, UUID strategy) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_portfolio")
            .where(QueryBuilder.eq("contract_id", contract))
            .and(QueryBuilder.eq("strategy_id", strategy));
        cqlTemplate.execute(delete);
    }

}
