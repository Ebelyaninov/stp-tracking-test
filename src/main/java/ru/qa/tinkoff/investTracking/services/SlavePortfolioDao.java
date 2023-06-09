package ru.qa.tinkoff.investTracking.services;


import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.rowmapper.ChangedAtSlavePortfolioRowMapper;
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
    private final ChangedAtSlavePortfolioRowMapper changedAtSlavePortfolioRowMapper;

    @Step("Находим запись в slave_portfolio: ")
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


    public SlavePortfolio getLatestSlavePortfolioWithVersion(String contractId, UUID strategyId, int version) {
        String query = "select * " +
            "from invest_tracking.slave_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "  and version = ? " +
            "order by version DESC, " +
            "compared_to_master_version DESC " +
            "limit 1";
        return cqlTemplate.queryForObject(query, slavePortfolioRowMapper, contractId, strategyId, version);
    }

    public SlavePortfolio getLatestSlavePortfolioBefore(String contractId, UUID strategyId, Date cutDate) {
        String query = "select * from invest_tracking.changed_at_slave_portfolio" +
            " where contract_id = ? " +
            " and strategy_id = ? " +
            " and changed_at < ? " +
            "order by changed_at desc, version desc," +
            " compared_to_master_version desc limit 1";;
        return cqlTemplate.queryForObject(query, changedAtSlavePortfolioRowMapper, contractId, strategyId, cutDate);
    }
    public SlavePortfolio getLatestSlavePortfolioAfter(String contractId, UUID strategyId, Date cutDate) {
        String query = "select * from invest_tracking.changed_at_slave_portfolio" +
            " where contract_id = ? " +
            " and strategy_id = ? " +
            " and changed_at >= ? " +
            "order by changed_at ASC, version ASC," +
            " compared_to_master_version ASC limit 1";;
        return cqlTemplate.queryForObject(query, changedAtSlavePortfolioRowMapper, contractId, strategyId, cutDate);
    }


    public SlavePortfolio getLatestSlavePortfolioByVersion(String contractId, UUID strategyId, int version ) {
        String query = "select * from invest_tracking.slave_portfolio" +
            " where contract_id = ? " +
            " and strategy_id = ? " +
            " and version = ? " +
            " order by version ASC," +
            " compared_to_master_version ASC limit 1";;
        return cqlTemplate.queryForObject(query, changedAtSlavePortfolioRowMapper, contractId, strategyId, version);
    }

    public SlavePortfolio getLatestSlavePortfolio(String contractId, UUID strategyId, Date start, Date end) {
        String query = "select * from invest_tracking.changed_at_slave_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "  and changed_at >= ? " +
            "  and changed_at < ? " +
            "order by changed_at desc, version desc, compared_to_master_version desc";
        return cqlTemplate.queryForObject(query, changedAtSlavePortfolioRowMapper, contractId, strategyId, start, end);
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


    @SneakyThrows
    public Optional<SlavePortfolio> findLatestSlavePortfolioWithVersion(String contractId, UUID strategyId, int version) {
        String query = "select * " +
            "from invest_tracking.slave_portfolio " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "  and version = ? " +
            "order by version DESC, " +
            "compared_to_master_version DESC " +
            "limit 1";
        List<SlavePortfolio> result = cqlTemplate.query(query, slavePortfolioRowMapper, contractId, strategyId, version);
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
        Statement insertQueryBuilder = QueryBuilder.insertInto("slave_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("compared_to_master_version", comparedToMasterVersion)
            .value("base_money_position", baseMoneyPosition)
            .value("positions", positionList)
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(insertQueryBuilder);
    }

    public void insertIntoSlavePortfolioWithoutPosition(String contractId, UUID strategyId, int version,
                                         int comparedToMasterVersion,
                                         SlavePortfolio.BaseMoneyPosition baseMoneyPosition,Date time) {
        Statement insertQueryBuilder = QueryBuilder.insertInto("slave_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("compared_to_master_version", comparedToMasterVersion)
            .value("changed_at", time)
            .value("base_money_position", baseMoneyPosition)
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(insertQueryBuilder);
    }

    public void insertIntoSlavePortfolioWithChangedAt(String contractId, UUID strategyId, int version,
                                         int comparedToMasterVersion,
                                         SlavePortfolio.BaseMoneyPosition baseMoneyPosition,
                                         List<SlavePortfolio.Position> positionList, Date time) {
        Statement insertQueryBuilder = QueryBuilder.insertInto("slave_portfolio")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("version", version)
            .value("compared_to_master_version", comparedToMasterVersion)
            .value("base_money_position", baseMoneyPosition)
            .value("changed_at", time)
            .value("positions",positionList)
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(insertQueryBuilder);
    }

    public void deleteSlavePortfolio(String contract, UUID strategy) {
        Statement delete = QueryBuilder.delete()
            .from("slave_portfolio")
            .where(QueryBuilder.eq("contract_id", contract))
            .and(QueryBuilder.eq("strategy_id", strategy))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(delete);
    }
}
