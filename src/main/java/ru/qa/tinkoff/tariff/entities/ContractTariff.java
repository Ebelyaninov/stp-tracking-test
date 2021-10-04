package ru.qa.tinkoff.tariff.entities;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.time.OffsetDateTime;



@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "contract_tariff")
@Table(name = "contract_tariff", schema = "tariff")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContractTariff {

    @NotNull
    @Id
    @SequenceGenerator(
        name = "tariff.contract_tariff_id_seq",
        sequenceName = "tariff.contract_tariff_id_seq",
        allocationSize = 1
    )
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "tariff.contract_tariff_id_seq"
    )
    BigInteger id;

    @NotNull
    @Column(name = "tariff_id")
    Integer tariffId;

    @NotNull
    @Column(name = "contract_id")
    String contractId;

    @NotNull
    @Column(name = "start_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS XXX")
    OffsetDateTime startDate;

    @NotNull
    @Column(name = "end_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS XXX")
    OffsetDateTime endDate;

}
