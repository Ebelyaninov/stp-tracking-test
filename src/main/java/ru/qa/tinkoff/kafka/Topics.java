package ru.qa.tinkoff.kafka;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Accessors(chain = true)
@RequiredArgsConstructor
public enum Topics {
    EXCHANGE_POSITION("tracking.exchange-position"),
    FIREG_INSTRUMENT("fireg.instrument"),
    TRACKING_MASTER_COMMAND("tracking.master.command"),
    TRACKING_SLAVE_COMMAND("tracking.slave.command"),
    TRACKING_EVENT("tracking.event"),
    TRACKING_30_DELAY_RETRYER_COMMAND("tracking.30.delay.retryer.command"),
    TRACKING_DELAY_COMMAND("tracking.delay.command"),
    TRACKING_SPB_RETRYER_COMMAND("tracking.spb.retryer.command"),
    TRACKING_MOEX_RETRYER_COMMAND("tracking.moex.retryer.command"),
    TRACKING_MOEXPLUS_RETRYER_COMMAND("tracking.moex-plus.retryer.command"),
    TRACKING_TEST_MD_PRICES_INT_STREAM("tracking.test.md.prices.int.stream"),
    TRACKING_ANALYTICS_COMMAND("tracking.analytics.command"),
    SOCIAL_EVENT("social.event"),
    TRACKING_SPB_MORNING_RETRYER_COMMAND("tracking.spb-morning.retryer.command"),
    TRACKING_FX_RETRYER_COMMAND("tracking.fx.retryer.command"),
    TRACKING_FEE_COMMAND("tracking.fee.command"),
    MIOF_POSITIONS_RAW("miof.positions.raw"),
    TRACKING_CONTRACT_EVENT("tracking.contract.event"),
    TRACKING_FEE_CALCULATE_COMMAND("tracking.fee.calculate.command"),
    ORIGINATION_SIGNATURE_NOTIFICATION("origination.signature.notification.raw"),
    TRACKING_SUBSCRIPTION_EVENT("tracking.subscription.event"),
    TRACKING_STRATEGY_EVENT("tracking.strategy.event"),
    ACCOUNT_REGISTRATION_EVENT("account.registration.event"),
    TRACKING_CONSUMER_COMMAND("tracking.consumer.command"),
    CCYEV("CCYEV"),

    ;
    private final String name;
}