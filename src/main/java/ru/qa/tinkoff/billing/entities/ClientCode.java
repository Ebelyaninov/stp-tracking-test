package ru.qa.tinkoff.billing.entities;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;





@Data
@Accessors(chain = true)
@Entity
@Table(name = "client_code", schema = "account")


public class ClientCode {

    @Id
    String id;

    @Column(name = "exchange_section")
    String exchangeSection;


    @Column(name = "broker_account_id")
    String brokerAccountId;


    @Column(name = "exchange_code")
    String exchangeCode;


}
