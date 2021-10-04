package ru.qa.tinkoff.tracking.entities;
import com.vladmihalcea.hibernate.type.range.Range;
import com.vladmihalcea.hibernate.type.range.PostgreSQLRangeType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.qa.tinkoff.PostgreSQLEnumType;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionBlockReason;


import javax.persistence.*;
import java.sql.Timestamp;



@Data
@Accessors(chain = true)
@Table(name = "subscription_block", schema = "tracking")
@Entity

@TypeDef(
    name = "pgsql_enum",
    typeClass = PostgreSQLEnumType.class)
@TypeDef(
    name = "daterange",
    typeClass = PostgreSQLRangeType.class
)

public class SubscriptionBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;


    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "reason")
    private String reason;

    @Type(type = "daterange")
    @Column(name = "period")
    private Range period;
}
