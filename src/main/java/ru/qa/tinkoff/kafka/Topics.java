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
    TRACKING_SLAVE_PORTFOLIO("tracking.slave.portfolio"),
    TRACKING_EVENT("tracking.event"),
    TRACKING_30_DELAY_RETRYER_COMMAND("tracking.30.delay.retryer.command"),
    TRACKING_DELAY_COMMAND("tracking.delay.command"),
    TRACKING_SPB_RETRYER_COMMAND("tracking.spb.retryer.command"),
    TRACKING_MOEX_RETRYER_COMMAND("tracking.moex.retryer.command"),
    TRACKING_MOEXPLUS_RETRYER_COMMAND("tracking.moex-plus.retryer.command"),
    TRACKING_MOEX_MORNING_RETRYER_COMMAND("tracking.moex-morning.retryer.command"),
    TRACKING_TEST_MD_PRICES_INT_STREAM("tracking.test.md.prices.int.stream"),
    TRACKING_ANALYTICS_COMMAND("tracking.analytics.command"),
    SOCIAL_EVENT("social.event"),
    TRACKING_SPB_MORNING_RETRYER_COMMAND("tracking.spb-morning.retryer.command"),
    TRACKING_SPB_MORNING_WEEKEND_RETRYER_COMMAND("tracking.spb-morning-weekend.retryer.command"),
    TRACKING_SPB_WEEKEND_RETRYER_COMMAND("tracking.spb-weekend.retryer.command"),
    TRACKING_SPB_RU_MORNING_RETRYER_COMMAND("tracking.spb-ru-morning.retryer.command"),
    TRACKING_FX_RETRYER_COMMAND("tracking.fx.retryer.command"),
    TRACKING_FX_WEEKEND_RETRYER_COMMAND("tracking.fx-weekend.retryer.command"),
    TRACKING_FX_MTL_RETRYER_COMMAND("tracking.fx-mtl.retryer.command"),
    TRACKING_MOEX_WEEKEND_RETRYER_COMMAND("tracking.moex-weekend.retryer.command"),
    TRACKING_MOEX_PLUS_WEEKEND_RETRYER_COMMAND("tracking.moex-plus-weekend.retryer.command"),
    TRACKING_FEE_COMMAND("tracking.fee.command"),
    MIOF_POSITIONS_RAW("miof.positions.raw"),
    TRACKING_CONTRACT_EVENT("tracking.contract.event"),
    TRACKING_FEE_CALCULATE_COMMAND("tracking.fee.calculate.command"),
    ORIGINATION_SIGNATURE_NOTIFICATION("origination.signature.notification.raw"),
    TRACKING_SUBSCRIPTION_EVENT("tracking.subscription.event"),
    TRACKING_STRATEGY_EVENT("tracking.strategy.event"),
    ORIGINATION_ACCOUNT_REGISTRATION_EVENT("origination.account.registration.event"),
    TRACKING_CONSUMER_COMMAND("tracking.consumer.command"),
    CCYEV("CCYEV"),
    ORIGINATION_TESTING_RISK_NOTIFICATION_RAW("origination.testing.risk.notification.raw"),
    TARIFF_CHANGE_RAW("tariff.change.raw"),
    NC_INPUT("nc.input"),
    TRACKING_CORP_ACTION_COMMAND("tracking.corp-action.command"),
    TRACKING_CLIENT_COMMAND("tracking.client.command"),
    MD_MOEX_PROTO_OB_FULL_STREAM("md.moex.proto.ob.full.stream"),
    MD_RTS_PROTO_OB_FULL_STREAM("md.rts.proto.ob.full.stream"),
    TRACKING_ORDERBOOK("tracking.orderbook"),
    TEST_TOPIC_TO_DELETE("test.topic.to.delete"),
    MASTER_PORTFOLIO_OPERATION("tracking.master.portfolio.operation")
    ;
    private final String name;
}