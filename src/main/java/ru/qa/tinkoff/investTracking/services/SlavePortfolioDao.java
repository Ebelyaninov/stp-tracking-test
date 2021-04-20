package ru.qa.tinkoff.investTracking.services;


import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.rowmapper.SlavePortfolioRowMapper;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SlavePortfolioDao {

    private final CqlTemplate cqlTemplate;
    private final SlavePortfolioRowMapper slavePortfolioRowMapper;

    public SlavePortfolio getLatestSlavePortfolio(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version DESC, " +
            "compared_to_master_version DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slavePortfolioRowMapper, contractId, strategyId);
    }


    @SneakyThrows
    public Optional<SlavePortfolio> findLatestSlavePortfolio(String contractId, UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.slave_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "order by version DESC, " +
            "compared_to_master_version DESC " +
            "limit 1";
        List<SlavePortfolio> result = cqlTemplate.query(query, slavePortfolioRowMapper, contractId, strategyId);
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));

    }


    public void insertIntoSlavePortfolio(String contractId, UUID strategyId, int version,
                                         int comparedToMasterVersion,
                                         SlavePortfolio.BaseMoneyPosition baseMoneyPosition,
                                         List<SlavePortfolio.Position> positionList) {
        Insert insertQueryBuider = QueryBuilder.insertInto("slave_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("compared_to_master_version", comparedToMasterVersion)
            .value("base_money_position", baseMoneyPosition)
            .value("positions",positionList);
        cqlTemplate.execute(insertQueryBuider);
    }

    public void insertIntoSlavePortfolioWithChangedAt(String contractId, UUID strategyId, int version,
                                         int comparedToMasterVersion,
                                         SlavePortfolio.BaseMoneyPosition baseMoneyPosition,
                                         List<SlavePortfolio.Position> positionList, Date time) {
        Insert insertQueryBuider = QueryBuilder.insertInto("slave_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("compared_to_master_version", comparedToMasterVersion)
            .value("base_money_position", baseMoneyPosition)
            .value("changed_at", time)
            .value("positions",positionList);
        cqlTemplate.execute(insertQueryBuider);
    }

    public void deleteSlavePortfolio(String contract, UUID strategy) {
        Delete.Where delete = QueryBuilder.delete()
            .from("slave_portfolio")
            .where(QueryBuilder.eq("contract_id", contract))
            .and(QueryBuilder.eq("strategy_id", strategy));
        cqlTemplate.execute(delete);
    }
}
