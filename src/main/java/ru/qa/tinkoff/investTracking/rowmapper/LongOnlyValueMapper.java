package ru.qa.tinkoff.investTracking.rowmapper;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.cassandra.core.cql.ResultSetExtractor;
import org.springframework.stereotype.Component;

@Component
public class LongOnlyValueMapper implements ResultSetExtractor<Long> {

    @Override
    public Long extractData(ResultSet resultSet) throws DriverException, DataAccessException {
        Row row = resultSet.one();
        return row.get(0, long.class);
    }
}
