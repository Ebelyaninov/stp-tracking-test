package ru.qa.tinkoff.tracking.entities;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.qa.tinkoff.PostgreSQLEnumType;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;

import javax.persistence.*;
import java.util.UUID;

@Data
@Accessors(chain = true)
@Table(schema = "tracking")
@Entity(name = "contract")
@TypeDef(
    name = "pgsql_enum",
    typeClass = PostgreSQLEnumType.class
)
public class Contract {
    @Id
    String id;

    @Type( type = "pgsql_enum" )
    @Enumerated(EnumType.STRING)
    ContractRole role;

    @Type( type = "pgsql_enum" )
    @Enumerated(EnumType.STRING)
    ContractState state;

    @Column(name = "client_id")
    UUID clientId;

    @Column(name = "strategy_id")
    UUID strategyId;

    @Column(name = "blocked")
    Boolean blocked;

//    @OneToOne(cascade = CascadeType.ALL)
//    @JoinColumn(name = "strategy_id", referencedColumnName = "id")
//    Strategy strategy;
}
