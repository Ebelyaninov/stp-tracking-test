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
    TRACKING_ANALYTICS_COMMAND("tracking.analytics.command");
    private final String name;
}