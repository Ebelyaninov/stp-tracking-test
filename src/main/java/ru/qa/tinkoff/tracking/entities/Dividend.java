package ru.qa.tinkoff.tracking.entities;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.TypeDef;
import ru.qa.tinkoff.PostgreSQLEnumType;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@Accessors(chain = true)
@Table(name = "dividend", schema = "corp_action")
@Entity(name = "dividend")
@TypeDef(
    name = "pgsql_enum",
    typeClass = PostgreSQLEnumType.class
)
public class Dividend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @Column(name = "strategy_id")
    UUID strategyId;

    @Column(name = "created_at")
    Timestamp createdAt;
}
