package ru.qa.tinkoff.billing.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Table(name = "invest_account", schema = "account")
public class InvestAccount {
    @Id
    UUID id;

    @Column(name = "siebel_id")
    String siebelId;

    @OneToMany(mappedBy = "investAccount")
    @JsonIgnore
    Set<BrokerAccount> brokerAccount;
}
