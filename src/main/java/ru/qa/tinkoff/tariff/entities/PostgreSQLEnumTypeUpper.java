package ru.qa.tinkoff.tariff.entities;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.EnumType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;


public class PostgreSQLEnumTypeUpper extends EnumType {

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names,
                              SharedSessionContractImplementor session,
                              Object owner) throws SQLException {
        String label = rs.getString(names[0]);
        if (rs.wasNull()) {
            return null;
        }
        return Enum.valueOf(returnedClass(), label.toUpperCase());
    }

    @Override
    public void nullSafeSet(
        PreparedStatement st,
        Object value,
        int index,
        SharedSessionContractImplementor session)
        throws HibernateException, SQLException {
        st.setObject(
            index,
            value != null ? ((Enum) value).name().toLowerCase() : null,
            Types.OTHER
        );
    }
}
