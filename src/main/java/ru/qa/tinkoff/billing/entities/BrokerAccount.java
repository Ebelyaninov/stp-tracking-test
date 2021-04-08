package ru.qa.tinkoff.billing.entities;

import lombok.Data;
import ru.qa.tinkoff.billing.converters.BrokerAccountStatusConverter;
import ru.qa.tinkoff.billing.converters.BrokerAccountTypeConverter;
import ru.qa.tinkoff.billing.entities.enums.BrokerAccountStatus;
import ru.qa.tinkoff.billing.entities.enums.BrokerAccountType;

import javax.persistence.*;

@Data
@Entity
@Table(name = "broker_account", schema = "account")
public class BrokerAccount {
    @Id
    String id;

    @Convert(converter = BrokerAccountTypeConverter.class)
    BrokerAccountType type;

    @Convert(converter = BrokerAccountStatusConverter.class)
    BrokerAccountStatus status;

    @ManyToOne
    @JoinColumn(name = "invest_id")
    InvestAccount investAccount;
}
