package ru.qa.tinkoff.billing.converters;

import ru.qa.tinkoff.billing.entities.enums.BrokerAccountStatus;

import javax.persistence.AttributeConverter;

public class BrokerAccountStatusConverter implements AttributeConverter<BrokerAccountStatus, String> {
    @Override
    public String convertToDatabaseColumn(BrokerAccountStatus attribute) {
        return attribute.getValue();
    }

    @Override
    public BrokerAccountStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            throw new IllegalArgumentException();
        }
        for (BrokerAccountStatus status : BrokerAccountStatus.values()) {
            if (dbData.equals(status.getValue())) {
                return status;
            }
        }
        throw new IllegalArgumentException();
    }
}
