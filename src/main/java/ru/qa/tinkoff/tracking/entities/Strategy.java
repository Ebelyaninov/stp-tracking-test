package ru.qa.tinkoff.tracking.entities;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.qa.tinkoff.PostgreSQLEnumType;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Accessors(chain = true)
@Table(name = "strategy", schema = "tracking")
@Entity(name = "strategy")
@TypeDef(
    name = "pgsql_enum",
    typeClass = PostgreSQLEnumType.class
)
public class Strategy {
    @Id
    UUID id;

    //    @OneToOne(fetch = FetchType.LAZY)
    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "contract_id")
    Contract contract;

    @Column(name = "title")
    String title;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency")
    StrategyCurrency baseCurrency;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile")
    StrategyRiskProfile riskProfile;

    @Column(name = "description")
    String description;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    StrategyStatus status;

    @Column(name = "slaves_count")
    Integer slavesCount;

    @Column(name = "activation_time")
    LocalDateTime activationTime;

    @Column(name = "score")
    Integer score;

    @Type(type = "jsonb")
    @Column(name = "fee_rate", columnDefinition = "jsonb")
    Map<String, BigDecimal> feeRate;

    @Generated(GenerationTime.INSERT)
    @Column(name = "position", insertable = false, updatable = false)
    Integer position;

    @Column(name = "overloaded")
    Boolean overloaded;

//    @Type( type = "jsonb" )
//    @Column(name = "fee_rate", columnDefinition = "jsonb")
//    Map<String, BigDecimal> feeRate;
////    String feeRate;

    @Column(name = "expected_relative_yield")
    BigDecimal expectedRelativeYield;

}
