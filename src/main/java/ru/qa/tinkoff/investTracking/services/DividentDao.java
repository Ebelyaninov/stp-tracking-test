package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.Dividend;
import ru.qa.tinkoff.investTracking.rowmapper.DividentRowMapper;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DividentDao {

    private final CqlTemplate cqlTemplate;
    private final DividentRowMapper dividentRowMapper;
    private final ObjectMapper objectMapper;

    @Step("Поиск дивидентов по contractId: {contractId} и strategyId: {strategyId}")
    @SneakyThrows
    public List<Dividend> findAllDividend (String contractId, UUID strategyId) {
        String query = "SELECT * FROM invest_tracking.dividend " +
            "WHERE contract_id = ? AND strategy_id = ?;";
        return cqlTemplate.query(query, dividentRowMapper, contractId, strategyId);
    }


    public void deleteAllDividendByContractAndStrategyId(String contract, UUID strategy) {
        Statement delete = QueryBuilder.delete()
            .from("dividend")
            .where(QueryBuilder.eq("contract_id", contract))
            .and(QueryBuilder.eq("strategy_id", strategy))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(delete);
    }

    public void insertIntoDividend(String contractId, UUID strategyId, Long id, Dividend.Context context) {
        Statement insertQueryBuilder = QueryBuilder.insertInto("dividend")
            .value("contract_id", contractId)
            .value("strategy_id", strategyId)
            .value("id", id)
            .value("context", getContextAsJsonString(context))
            .setConsistencyLevel(ConsistencyLevel.EACH_QUORUM);
        cqlTemplate.execute(insertQueryBuilder);
    }

    @Step("Переформатируем context в json и потом в стрингу")
    @SneakyThrows
    public String getContextAsJsonString (Dividend.Context context) {
        return objectMapper.writeValueAsString(context);
    }
}
