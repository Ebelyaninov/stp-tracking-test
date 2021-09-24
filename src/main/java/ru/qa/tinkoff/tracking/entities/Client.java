package ru.qa.tinkoff.tracking.entities;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.vladmihalcea.hibernate.type.json.JsonStringType;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.val;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import ru.qa.tinkoff.PostgreSQLEnumType;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.tracking.entities.enums.ClientRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
@Entity(name = "client")

public class Client {
    @Id
    UUID id;

    @Type( type = "pgsql_enum" )
    @Enumerated(EnumType.STRING)
    @Column(name = "master_status")
    ClientStatusType masterStatus;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    List<Contract> contracts = new ArrayList<>();


    @Type( type = "jsonb" )
    @Column(name = "social_profile", columnDefinition = "jsonb")
    SocialProfile socialProfile;

    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile")
    ClientRiskProfile riskProfile;


    public Client addContract(Contract contract) {
        contract.setClientId(id);
        contracts.add(contract);
        return this;
    }

    public static Client getDefault() {
        return new Client()
            .setId(UUID.randomUUID())
            .setMasterStatus(ClientStatusType.confirmed);
    }

}
