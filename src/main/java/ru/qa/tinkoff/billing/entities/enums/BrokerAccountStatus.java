package ru.qa.tinkoff.billing.entities.enums;

import lombok.Getter;

public enum BrokerAccountStatus {
    NEW("new"),
    OPENED("opened"),
    CLOSED("closed");

    @Getter
    private String value;

    BrokerAccountStatus(String status) {
        this.value = status;
    }
}
