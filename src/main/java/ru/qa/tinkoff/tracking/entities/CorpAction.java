package ru.qa.tinkoff.tracking.entities;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.TypeDef;
import ru.qa.tinkoff.PostgreSQLEnumType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Accessors(chain = true)
@Table(name = "corp_action", schema = "corp_action")
@Entity(name = "corp_action")
@TypeDef(
    name = "pgsql_enum",
    typeClass = PostgreSQLEnumType.class
)
public class CorpAction {
    @Id
    UUID strategyId;

    @Column(name = "cut")
    LocalDateTime cut;

    @Column(name = "type")
    String type;

}
