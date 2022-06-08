package ru.qa.tinkoff.investTracking.services;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.DriverException;
import lombok.AllArgsConstructor;
import org.springframework.data.cassandra.core.cql.PreparedStatementCreator;

@AllArgsConstructor
public class ConsistencyLevelCreator implements PreparedStatementCreator {

    private final PreparedStatementCreator creator;
    private final ConsistencyLevel consistency;

    @Override
    public PreparedStatement createPreparedStatement(Session session) throws DriverException {
        PreparedStatement preparedStatement = creator.createPreparedStatement(session);
        preparedStatement.setConsistencyLevel(consistency);
        return preparedStatement;
    }
}
