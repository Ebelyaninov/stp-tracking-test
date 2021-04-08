package ru.qa.tinkoff.billing.converters;

import ru.qa.tinkoff.billing.entities.enums.BrokerAccountType;

import javax.persistence.AttributeConverter;

public class BrokerAccountTypeConverter implements AttributeConverter<BrokerAccountType, String> {
    @Override
    public String convertToDatabaseColumn(BrokerAccountType attribute) {
        return attribute.getValue();
    }

    @Override
    public BrokerAccountType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            throw new IllegalArgumentException();
        }
        for (BrokerAccountType type : BrokerAccountType.values()) {
            if (dbData.equals(type.getValue())) {
                return type;
            }
        }
        throw new IllegalArgumentException();
    }
}
