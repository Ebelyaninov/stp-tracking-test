package ru.qa.tinkoff.tracking.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum  SubscriptionBlockReason {
    RISK_PROFILE ("risk-profile");

    private final String alias;
}