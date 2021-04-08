package ru.qa.tinkoff.tracking.entities;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.qa.tinkoff.PostgreSQLEnumType;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;

import javax.persistence.*;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@Accessors(chain = true)
@Table(name = "subscription", schema = "tracking")
@Entity
@TypeDef(
    name = "pgsql_enum",
    typeClass = PostgreSQLEnumType.class
)
public class Subscription {
    @Id
    BigInteger id;

//    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @Column(name = "slave_contract_id")
    String slaveContractId;

    @Column(name = "strategy_id")
    UUID strategyId;

    @Column(name = "start_time")
    Timestamp startTime;

    @Type( type = "pgsql_enum" )
    @Enumerated(EnumType.STRING)
    SubscriptionStatus status;

    @Column(name = "end_time")
    Timestamp endTime;

}
