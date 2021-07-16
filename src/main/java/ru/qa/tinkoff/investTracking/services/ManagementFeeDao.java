package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.Context;
import ru.qa.tinkoff.investTracking.entities.ManagementFee;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.rowmapper.ManagementFeeRowMapper;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class ManagementFeeDao {

    private final CqlTemplate cqlTemplate;
    private final ManagementFeeRowMapper managementFeeRowMapper;
    @Qualifier("contextMapper")
    private final ObjectMapper contextMapper;

    public ManagementFee getManagementFee(String contractId, UUID strategyId, Long subscriptionId, int version) {
        String query = "select * " +
            "FROM invest_tracking.management_fee " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "  and subscription_id = ? " +
            "  and version = ? " +
            "ORDER BY subscription_id, version DESC, " +
            "settlement_period_started_at DESC LIMIT 1 ";
        return cqlTemplate.queryForObject(query, managementFeeRowMapper, contractId, strategyId, subscriptionId, version);
    }

    public void deleteManagementFee(String contract, UUID strategy) {
        Delete.Where delete = QueryBuilder.delete()
            .from("management_fee")
            .where(QueryBuilder.eq("contract_id", contract))
            .and(QueryBuilder.eq("strategy_id", strategy));
        cqlTemplate.execute(delete);
    }


    @SneakyThrows
    public void insertIntoManagementFee(String contractId, UUID strategyId, long subscriptionId, int version,
                                        Date settlementPeriodStartedAt, Date settlementPeriodEndedAt, Context context) {
        String contextAsText = contextMapper.writeValueAsString(context);
        Insert insertQueryBuider = QueryBuilder.insertInto("management_fee")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("subscription_id", subscriptionId)
            .value("version", version)
            .value("settlement_period_started_at", settlementPeriodStartedAt)
            .value("settlement_period_ended_at", settlementPeriodEndedAt)
            .value("context", contextAsText);
        cqlTemplate.execute(insertQueryBuider);
    }

    @Step("Поиск портфеля в cassandra по contractId и strategyId")
    @SneakyThrows
    public Optional<ManagementFee> findManagementFee(String contractId, UUID strategyId, Long subscriptionId, int version) {
        String query = "select * " +
            "FROM invest_tracking.management_fee " +
            "where contract_id = ? " +
            "  and strategy_id = ? " +
            "  and subscription_id = ? " +
            "  and version = ? " +
            "ORDER BY subscription_id, version DESC, " +
            "settlement_period_started_at DESC LIMIT 1 ";
        List<ManagementFee> result = cqlTemplate.query(query, managementFeeRowMapper, contractId, strategyId, subscriptionId, version);;
        if (result.size() > 1) {
            throw new RuntimeException("Too many results");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.get(0));

    }






}