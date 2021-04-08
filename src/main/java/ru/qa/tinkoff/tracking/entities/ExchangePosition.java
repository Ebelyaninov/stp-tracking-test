package ru.qa.tinkoff.tracking.entities;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.vladmihalcea.hibernate.type.json.JsonStringType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import ru.qa.tinkoff.PostgreSQLEnumType;
import ru.qa.tinkoff.tracking.entities.enums.ExchangePositionExchange;


import javax.persistence.*;
import java.io.Serializable;
import java.util.Map;


@TypeDefs({
    @TypeDef(name = "json", typeClass = JsonStringType.class),
    @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class),
    @TypeDef(
        name = "pgsql_enum",
        typeClass = PostgreSQLEnumType.class
    )
})
@Data
@Accessors(chain = true)
@Table(schema = "tracking")
@Entity(name = "exchange_position")
@IdClass(ExchangePositionId.class)
public class ExchangePosition implements Serializable {

    @Id
    @Column(name = "ticker")
    String ticker;

    @Id
    @Column(name = "trading_clearing_account")
    String tradingClearingAccount;

    @Type( type = "pgsql_enum" )
    @Enumerated(EnumType.STRING)
    @Column(name = "exchange")
    ExchangePositionExchange exchangePositionExchange;

    @Column(name = "tracking_allowed")
    Boolean trackingAllowed;

    @Column(name = "daily_quantity_limit")
    Integer dailyQuantityLimit;

    @Type( type = "jsonb" )
    @Column(name = "order_quantity_limits", columnDefinition = "jsonb")
    Map<String, Integer> orderQuantityLimits;

    @Column(name = "otc_ticker")
    String otcTicker;

    @Column(name = "otc_class_code")
    String otcClassCode;
}
