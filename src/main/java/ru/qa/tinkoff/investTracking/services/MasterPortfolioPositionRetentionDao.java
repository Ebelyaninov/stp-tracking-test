package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.stereotype.Component;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioPositionRetention;
import ru.qa.tinkoff.investTracking.rowmapper.MasterPortfolioPositionRetentionRowMapper;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class MasterPortfolioPositionRetentionDao {

    private final CqlTemplate cqlTemplate;
    private final MasterPortfolioPositionRetentionRowMapper masterPortfolioPositionRetentionRowMapper;

    @Step("Поиск value из master position retention в cassandra по strategyId")
    @SneakyThrows
    public MasterPortfolioPositionRetention getMasterPortfolioPositionRetention(UUID strategyId) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_position_retention " +
            "where strategy_id = ? " +
            "limit 1";
        return cqlTemplate.queryForObject(query, masterPortfolioPositionRetentionRowMapper, strategyId);
    }

    @Step("Поиск value из master position retention в cassandra по strategyId и cut")
    @SneakyThrows
    public MasterPortfolioPositionRetention getMasterPortfolioPositionRetention(UUID strategyId, Date cut) {
        String query = "select * " +
            "from invest_tracking.master_portfolio_position_retention " +
            "where strategy_id = ? " +
            "  and cut = ? " +
            "limit 1";
        return cqlTemplate.queryForObject(query, masterPortfolioPositionRetentionRowMapper, strategyId, cut);
    }

    public void deleteMasterPortfolioPositionRetention(UUID strategy) {
        Delete.Where delete = QueryBuilder.delete()
            .from("master_portfolio_position_retention")
            .where(QueryBuilder.eq("strategy_id", strategy));
        cqlTemplate.execute(delete);
    }
}
