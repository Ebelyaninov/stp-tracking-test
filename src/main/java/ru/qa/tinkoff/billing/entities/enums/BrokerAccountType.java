package ru.qa.tinkoff.billing.entities.enums;

import lombok.Getter;

public enum BrokerAccountType {
    BROKER("broker"),
    IIS("iis"),
    INVEST_BOX("invest-box");

    @Getter
    private String value;


    BrokerAccountType(String value) {
        this.value = value;
    }
}