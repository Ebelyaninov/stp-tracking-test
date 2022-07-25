package ru.qa.tinkoff.tariff.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "tariff")
@Table(name = "tariff", schema = "tariff")
@TypeDefs(
    {
        @TypeDef(
            name = "pgsql_enum_upper",
            typeClass = PostgreSQLEnumTypeUpper.class
        )
    }
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(of = {"siebelId", "tariffType"})
public class Tariff {

    @NotNull
    @Id
    @Column(columnDefinition = "serial")
    Integer id;

    @NotNull
    @Column(name = "siebel_id")
    String siebelId;

    @NotNull
    @Column(name = "tariff_type")
    String tariffType;

    @NotNull
    @Column(name = "start_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS XXX")
    OffsetDateTime startDate;

    @Column(name = "end_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS XXX")
    OffsetDateTime endDate;

    @NotNull
    @Column(name = "service_fee_amount")
    BigDecimal serviceFeeAmount;

    @NotNull
    BigDecimal rate;

    @NotNull
    @Column(name = "require_service_fee")
    Character requireServiceFee;

    @NotNull
    @Column(name = "allow_otc")
    Character allowOtc;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum_upper")
    TariffType type;

    @NotNull
    String name;

    @NotNull
    @Column(name = "siebel_name")
    String siebelName;

    public enum TariffType {
        MM,
        WM,
        PIA,
        CAPITAL,
        ISA,
        INVEST_LAB,
        INSURANCE,
        PRIVATE,
        TRACKING,
        TINKOV_FAMILY_FUND,
        SME
    }
}
