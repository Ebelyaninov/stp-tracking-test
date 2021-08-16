package stpTrackingFee.calculateResultFee;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.*;
import ru.qa.tinkoff.investTracking.services.ManagementFeeDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.ResultFeeDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.SptTrackingFeeStepsConfiguration;
import ru.qa.tinkoff.steps.trackingFeeSteps.StpTrackingFeeSteps;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_FEE_CALCULATE_COMMAND;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_FEE_COMMAND;

@Slf4j
@Epic("calculateResultFee - Расчет комиссии за результат")
@Feature("TAP-9903")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-fee")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SptTrackingFeeStepsConfiguration.class
})
public class CalculateResultFeeTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingFeeSteps steps;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    ResultFeeDao resultFeeDao;

    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();

    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Subscription subscription;
    ResultFee resultFee;


    UUID strategyId;

    String siebelIdMaster = "1-51Q76AT";
    String siebelIdSlave = "5-1P87U0B13";
    String ticker1 = "SBER";
    String tradingClearingAccount1 = "L01+00002F00";
    String classCode1 = "TQBR";
    String instrumet1 = ticker1 + "_" + classCode1;
    String quantity1 = "20";

    public String ticker2 = "SU29009RMFS6";
    public String tradingClearingAccount2 = "L01+00002F00";
//    public String tradingClearingAccount2 = "L01+00000F00";
    public String quantity2 = "5";
    public String classCode2 = "TQOB";
    public String instrumet2 = ticker2 + "_" + classCode2;
    BigDecimal minPriceIncrement = new BigDecimal("0.001");


    String ticker3 = "YNDX";
//    String tradingClearingAccount3 = "L01+00000F00";
    String tradingClearingAccount3 = "L01+00002F00";
    String classCode3 = "TQBR";
    String instrumet3 = ticker3 + "_" + classCode3;
    String quantity3 = "5";




    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(steps.clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(steps.strategyMaster);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientMaster);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                resultFeeDao.deleteResultFee(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1081005")
    @DisplayName("C1081005.CalculateResultFee.Расчет комиссии за результат. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt <= метки времени от now(), по cron-выражению")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1081005() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40", ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime updateTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        subscription.setStartTime(new Timestamp(updateTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        createSlaveportfolio();
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //Расчитываем стоимость порфеля на конец первого расчетного периода
        BigDecimal valuePortfolioOnePeriod = createPortfolioValueOnePeriod();
        LocalDateTime lastDayFirstSecondPeriod = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioSecondPeriod = getPorfolioValue("43606.35", "20", "5", "5", lastDayFirstSecondPeriod);

        LocalDateTime lastDayFirstThirdPeriod = LocalDate.now().minusMonths(0).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioThirdPeriod = getPorfolioValue("31367.25", "10", "5", "8", lastDayFirstThirdPeriod);

       BigDecimal highWaterMarkFirstPeriodBefore = new BigDecimal("50000");
       BigDecimal adjustValueFirstPeriod = highWaterMarkFirstPeriodBefore.add(new BigDecimal("10000"));
       BigDecimal highWaterMarkFirstPeriod = adjustValueFirstPeriod.max(valuePortfolioOnePeriod);
        BigDecimal highWaterMarkSecondPeriodBefore = highWaterMarkFirstPeriod;
        BigDecimal adjustValueSecondPeriod = highWaterMarkSecondPeriodBefore.add(new BigDecimal("15000")) ;
        BigDecimal highWaterMarkSecondPeriod = adjustValueSecondPeriod.max(valuePortfolioSecondPeriod);
        BigDecimal highWaterMarkThirdPeriodBefore = highWaterMarkSecondPeriod;
        BigDecimal highWaterMarkThirdPeriod = highWaterMarkThirdPeriodBefore.max(valuePortfolioThirdPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 4);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioOnePeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkFirstPeriod));
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioSecondPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkSecondPeriod));
         resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioThirdPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkThirdPeriod));
    }



    @SneakyThrows
    @Test
    @AllureId("1081856")
    @DisplayName("C1081856.CalculateResultFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt = найденный end_time")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1081856() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        subscription.setStartTime(new Timestamp(startSubTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        subscription.setEndTime(new Timestamp(endSubTime.toInstant().toEpochMilli())).setStatus(SubscriptionStatus.inactive);
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        createSlaveportfolio();
        //обавляем еще пару версий за текущий месяц
        List<SlavePortfolio.Position> positionSlaveListVersionTen = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "10","285.51", ticker2, tradingClearingAccount2, "8", "105.29",
            ticker3,  tradingClearingAccount3, "8","5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTen = steps.createBaseMoney("31051.38",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 10,
            3, baseMoneyVersionTen, positionSlaveListVersionTen, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()));
        List<SlavePortfolio.Position> positionSlaveListVersionEleven = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "10","285.51", ticker2, tradingClearingAccount2, "8", "105.29",
            ticker3,  tradingClearingAccount3, "8","5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEleven = steps.createBaseMoney("34051.38",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 11,
            3, baseMoneyVersionEleven, positionSlaveListVersionEleven, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //Расчитываем стоимость порфеля на конец расчетного периода
        BigDecimal valuePortfolioFourPeriod = getPorfolioValue("34051.38", "10", "8", "8", endSubTime.toLocalDateTime());
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        BigDecimal highWaterMarkFourPeriodBefore = resultFee.getHighWaterMark();
        BigDecimal adjustValueFourPeriod = highWaterMarkFourPeriodBefore.add(new BigDecimal("3000")) ;
        BigDecimal highWaterMarkFourPeriod = adjustValueFourPeriod.max(valuePortfolioFourPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 11);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioFourPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkFourPeriod));
    }




    @SneakyThrows
    @Test
    @AllureId("1080986")
    @DisplayName("C1080986.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = > метки времени от settlement_period_started_at," +
        " endedAt <= метка времени от now(), по cron-выражению")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1080986() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime updateTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        subscription.setStartTime(new Timestamp(updateTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        createSlaveportfolio();
        //добавляем записи в result_fee
        createFeeResultFirst(updateTime, subscriptionId);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //Расчитываем стоимость порфеля на конец расчетного периода
        LocalDateTime lastDayFirstSecondPeriod = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioSecondPeriod = getPorfolioValue("43606.35", "20", "5", "5", lastDayFirstSecondPeriod);
        LocalDateTime lastDayFirstThirdPeriod = LocalDate.now().minusMonths(0).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioThirdPeriod = getPorfolioValue("31367.25", "10", "5", "8", lastDayFirstThirdPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 4);
        BigDecimal highWaterMarkSecondPeriodBefore = resultFee.getHighWaterMark();
        BigDecimal adjustValueSecondPeriod = highWaterMarkSecondPeriodBefore.add(new BigDecimal("15000")) ;
        BigDecimal highWaterMarkSecondPeriod = adjustValueSecondPeriod.max(valuePortfolioSecondPeriod);
        BigDecimal highWaterMarkThirdPeriodBefore = highWaterMarkSecondPeriod;
        BigDecimal highWaterMarkThirdPeriod = highWaterMarkThirdPeriodBefore.max(valuePortfolioThirdPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioSecondPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark().compareTo(highWaterMarkSecondPeriod), is(0));
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioThirdPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkThirdPeriod));
    }


    @SneakyThrows
    @Test
    @AllureId("1093069")
    @DisplayName("C1093069.CalculateResultFee.Определения расчетных периодов.startedAt = > " +
        "метки времени от settlement_period_started_at, endedAt <= метка времени от now(), по cron-выражению")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1093069() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        subscription.setStartTime(new Timestamp(startSubTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        subscription.setEndTime(new Timestamp(endSubTime.toInstant().toEpochMilli())).setStatus(SubscriptionStatus.inactive);
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        createSlaveportfolio();
        //обавляем еще пару версий за текущий месяц
        List<SlavePortfolio.Position> positionSlaveListVersionTen = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "10","285.51", ticker2, tradingClearingAccount2, "8", "105.29",
            ticker3,  tradingClearingAccount3, "8","5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTen = steps.createBaseMoney("31051.38",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 10,
            3, baseMoneyVersionTen, positionSlaveListVersionTen, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()));
        List<SlavePortfolio.Position> positionSlaveListVersionEleven = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "10","285.51", ticker2, tradingClearingAccount2, "8", "105.29",
            ticker3,  tradingClearingAccount3, "8","5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEleven = steps.createBaseMoney("34051.38",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 11,
            3, baseMoneyVersionEleven, positionSlaveListVersionEleven, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        //добавляем записи в result_fee
        createTwoPeriodFeeResult (startSubTime,  subscriptionId);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        LocalDateTime lastDayFirstThirdPeriod = LocalDate.now().minusMonths(0).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioThirdPeriod = getPorfolioValue("31367.25", "10", "5", "8", lastDayFirstThirdPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7);
        BigDecimal highWaterMarkThirdPeriodBefore = resultFee.getHighWaterMark();
        BigDecimal highWaterMarkThirdPeriod = highWaterMarkThirdPeriodBefore.max(valuePortfolioThirdPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioThirdPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkThirdPeriod));
        //Расчитываем стоимость порфеля на конец расчетного периода
        BigDecimal valuePortfolioFourPeriod = getPorfolioValue("34051.38", "10", "8", "8", endSubTime.toLocalDateTime());
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        BigDecimal highWaterMarkFourPeriodBefore = resultFee.getHighWaterMark();
        BigDecimal adjustValueFourPeriod = highWaterMarkFourPeriodBefore.add(new BigDecimal("3000")) ;
        BigDecimal highWaterMarkFourPeriod = adjustValueFourPeriod.max(valuePortfolioFourPeriod);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 11);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioFourPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkFourPeriod));
    }



    @SneakyThrows
    @Test
    @AllureId("1093785")
    @DisplayName("C1093785.CalculateResultFee.Формируем команду в топик tracking.fee.calculate.command")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1093785() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(5);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        subscription.setStartTime(new Timestamp(startSubTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        subscription.setEndTime(new Timestamp(endSubTime.toInstant().toEpochMilli())).setStatus(SubscriptionStatus.inactive);
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        byte[] strategyIdByteArray = feeCommand.getSubscription().getStrategy().getId().toByteArray();
        UUID guidFromByteArray = UtilsTest.getGuidFromByteArray(strategyIdByteArray);
        assertThat("subscription.strategy_id не равен", guidFromByteArray, is(strategyId));

        double rateResultScale = Math.pow(10, -1 * feeCommand.getRate().getScale());
        BigDecimal rateResult = BigDecimal.valueOf(feeCommand.getRate().getUnscaled()).multiply(BigDecimal.valueOf(rateResultScale));
        assertThat("rate result не равен", rateResult, is(new BigDecimal("0.2")));

        assertThat("rate unscaled  не равен", feeCommand.getRate().getUnscaled(), is(2L));
        assertThat("rate scale не равен", feeCommand.getRate().getScale(), is(1));

        assertThat("currency не равен", feeCommand.getCurrency().toString(), is("RUB"));
        LocalDateTime commandStartTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getStartedAt().getSeconds()), ZoneId.of("UTC"));
        LocalDateTime commandEndTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getEndedAt().getSeconds()), ZoneId.of("UTC"));
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(5).atStartOfDay().toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().toString()));

        resultFee = resultFeeDao.getResultFee(contractIdSlave, this.strategyId, subscriptionId, 3);
        BigDecimal portfolioValue = resultFee.getContext().getPortfolioValue();
        double scalePortfolio = Math.pow(10, -1 * feeCommand.getResult().getPortfolioValue().getScale());
        BigDecimal valuePortfolio = BigDecimal.valueOf(feeCommand.getResult().getPortfolioValue().getUnscaled()).multiply(BigDecimal.valueOf(scalePortfolio));
        double scale = Math.pow(10, -1 * feeCommand.getResult().getHighWaterMark().getScale());
        BigDecimal value = BigDecimal.valueOf(feeCommand.getResult().getHighWaterMark().getUnscaled()).multiply(BigDecimal.valueOf(scale));
        assertThat("value стоимости портфеля не равно", valuePortfolio, is(portfolioValue));
        assertThat("value high_water_mark портфеля не равно", value, is(new BigDecimal("25000.0")));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("1080987")
    @DisplayName("C1080987.CalculateResultFee.Формируем команду в топик tracking.fee.calculate.command")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1080987() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        subscription.setStartTime(new Timestamp(startSubTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        subscription.setEndTime(new Timestamp(endSubTime.toInstant().toEpochMilli())).setStatus(SubscriptionStatus.inactive);
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        Optional<ResultFee> portfolioValue = resultFeeDao.findResultFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("запись по расчету комиссии за управления не равно", portfolioValue.isPresent(), is(false));

    }

    @SneakyThrows
    @Test
    @AllureId("1095388")
    @DisplayName("C1095388.CalculateResultFee.Расчетный период еще не начался.StartedAt > now()")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1095388() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40", ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //изменяем дату создания подписки
        long subscriptionId = subscription.getId().longValue();
        OffsetDateTime updateTime = OffsetDateTime.now().minusMonths(1);
        subscription.setStartTime(new Timestamp(updateTime.toInstant().toEpochMilli()));
        subscriptionService.saveSubscription(subscription);
        //создаем портфели slave
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, "25000.0",
            positionList, Date.from(OffsetDateTime.now().minusDays(29).toInstant()));
        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, "18700.02",
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusDays(28).toInstant()));
        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositionsNoBond(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, "8974.42",
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusDays(27).toInstant()));
        Date startFirst = Date.from(updateTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 3,
            startFirst, endFirst, context, new BigDecimal("65162.5"));
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        createCommandResult(subscriptionId);
        // проверяем, что команда в tracking.fee.calculate.command не улетает
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }







    // методы для работы тестов*****************************************************************


    void createCommandResult(long subscriptionId) {
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommandResult(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
    }


    List<MasterPortfolio.Position> masterPositions(Date date, String tickerOne, String tradingClearingAccountOne,
                                                   String quantityOne, String tickerTwo, String tradingClearingAccountTwo,
                                                   String quantityTwo) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerOne)
            .tradingClearingAccount(tradingClearingAccountOne)
            .quantity(new BigDecimal(quantityOne))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerTwo)
            .tradingClearingAccount(tradingClearingAccountTwo)
            .quantity(new BigDecimal(quantityTwo))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }



    List<SlavePortfolio.Position> oneSlavePositions111(String ticker, String tradingClearingAccount, String quantity,
                                                       String price, Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantity))
            .price(new BigDecimal(price))
            .changedAt(date)
            .build());
        return positionList;
    }



    List<SlavePortfolio.Position> twoSlavePositions111(String ticker1, String tradingClearingAccount1, String quantity1,
                                                       String price1, String ticker2, String tradingClearingAccount2, String quantity2,
                                                       String price2, Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .price(new BigDecimal(price1))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .price(new BigDecimal(price2))
            .changedAt(date)
            .build());
        return positionList;
    }


    List<SlavePortfolio.Position> threeSlavePositions111(String ticker1, String tradingClearingAccount1, String quantity1,
                                                       String price1, String ticker2, String tradingClearingAccount2, String quantity2,
                                                       String price2, String ticker3, String tradingClearingAccount3, String quantity3,
                                                         String price3, Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .price(new BigDecimal(price1))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .price(new BigDecimal(price2))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .price(new BigDecimal(price3))
            .changedAt(date)
            .build());
        return positionList;
    }



    void createSlaveportfolio() {
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney("50000.0",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()));

        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(ticker1, tradingClearingAccount1, "20",
            "285.51",Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("442898",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()));

         List<SlavePortfolio.Position> positionListVersionThree = twoSlavePositions111(
            ticker1, tradingClearingAccount1, "20","285.51", ticker2, tradingClearingAccount2, "5",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionThree = steps.createBaseMoney("43763.35",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            3, baseMoneyVersionThree, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()));


        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney("53763.35",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 4,
            3, baseMoneyVersionFour, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(0).toInstant()));


        List<SlavePortfolio.Position> positionListVersionFive = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "20","285.51", ticker2, tradingClearingAccount2, "5", "105.29",
            ticker3,  tradingClearingAccount3, "5","5031.4", Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFive = steps.createBaseMoney("28606.35",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 5,
            3, baseMoneyVersionFive, positionListVersionFive, Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));


        SlavePortfolio.BaseMoneyPosition baseMoneyVersionSix = steps.createBaseMoney("38606.35",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 6,
            3, baseMoneyVersionSix, positionListVersionFive, Date.from(OffsetDateTime.now().minusMonths(2).minusDays(1).toInstant()));


        SlavePortfolio.BaseMoneyPosition baseMoneyVersionSeven = steps.createBaseMoney("43606.35",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 7,
            3, baseMoneyVersionSeven, positionListVersionFive, Date.from(OffsetDateTime.now().minusMonths(2).minusDays(0).toInstant()));

        List<SlavePortfolio.Position> positionListVersionEight = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "10","285.51", ticker2, tradingClearingAccount2, "5", "105.29",
            ticker3,  tradingClearingAccount3, "5","5031.4", Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEight = steps.createBaseMoney("46461.45",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 11);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 8,
            3, baseMoneyVersionEight, positionListVersionEight, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));

        List<SlavePortfolio.Position> positionSlaveVersionNine = threeSlavePositions111(ticker1,
            tradingClearingAccount1, "10","285.51", ticker2, tradingClearingAccount2, "5", "105.29",
            ticker3,  tradingClearingAccount3, "8","5031.4", Date.from(OffsetDateTime.now().minusMonths(1).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionNine = steps.createBaseMoney("31367.25",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 9,
            3, baseMoneyVersionNine, positionSlaveVersionNine, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(1).toInstant()));

    }




    BigDecimal createPortfolioValueOnePeriod() {
        //Расчитываем стоимость порфеля на конец первого расчетного периода
        // формируем список позиций для запроса prices MD
        LocalDateTime lastDayFirstPeriod = LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(lastDayFirstPeriod);
        String ListInst = instrumet1 + "," + instrumet2;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(lastDayFirstPeriod);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(classCode2)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal price1 = BigDecimal.ZERO;
        BigDecimal price2 = BigDecimal.ZERO;

        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrumet1)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantity2);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantity2);
                ;
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(new BigDecimal("53763.35"));
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }

    BigDecimal getPorfolioValueSecondPeriod(int monts, String baseMoney, String quantity1) {

        // формируем список позиций для запроса prices MD
        LocalDateTime lastDayFirstPeriod = LocalDate.now().minusMonths(monts).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(lastDayFirstPeriod);
        String ListInst = instrumet1 + "," + instrumet2 + "," + instrumet3;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 3);
        // получаем данные для расчета по облигациям
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(lastDayFirstPeriod);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(classCode2)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal price1 = BigDecimal.ZERO;
        BigDecimal price2 = BigDecimal.ZERO;
        BigDecimal price3 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrumet1)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantity2);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantity2);
            }
            if (pair.getKey().equals(instrumet3)) {
                valuePos3 = new BigDecimal(quantity3).multiply((BigDecimal) pair.getValue());
                price3 = (BigDecimal) pair.getValue();
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3).add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }


    BigDecimal getPorfolioValue(String baseMoney, String quantity1, String quantity2, String quantity3, LocalDateTime cutDate) {

        // формируем список позиций для запроса prices MD
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutDate);
        String ListInst = instrumet1 + "," + instrumet2 + "," + instrumet3;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 3);

        // получаем данные для расчета по облигациям
        DateTimeFormatter fmtFireg =  DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutDate);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(classCode2)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal price1 = BigDecimal.ZERO;
        BigDecimal price2 = BigDecimal.ZERO;
        BigDecimal price3 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrumet1)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantity2);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantity2);
            }
            if (pair.getKey().equals(instrumet3)) {
                valuePos3 = new BigDecimal(quantity3).multiply((BigDecimal) pair.getValue());
                price3 = (BigDecimal) pair.getValue();
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3).add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }


    void createFeeResultFirst(OffsetDateTime updateTime, long subscriptionId) {
        Date startFirst = Date.from(updateTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 4,
            startFirst, endFirst, context, new BigDecimal("65162.5"));
    }

    void createTwoPeriodFeeResult ( OffsetDateTime startSubTime, long subscriptionId) {
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 4,
            startFirst, endFirst, context, new BigDecimal("65162.5"));

        Date startSecond = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endSecond = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmptySecond = new ArrayList<>();
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("310.79"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1069.65000"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("4942"))
            .priceTs(startFirst)
            .build());
        Context contextSec = Context.builder()
            .portfolioValue(new BigDecimal("79880.40000"))
            .positions(positionListEmptySecond)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 7,
            startSecond, endSecond, contextSec, new BigDecimal("79880.4"));
    }


    void createSlavePOrtfolioNoBond(String baseMoneyOne, String baseMoneyTwo, String baseMoneyThree) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, baseMoneyOne,
            positionList, Date.from(OffsetDateTime.now().minusDays(4).toInstant()));

        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, baseMoneyTwo,
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));

        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositionsNoBond(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, baseMoneyThree,
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
    }

    void createSlavePOrtfolioNoBond111(String baseMoneyOne, String baseMoneyTwo, String baseMoneyThree) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, baseMoneyOne,
            positionList, Date.from(OffsetDateTime.now().minusMinutes(4).toInstant()));

        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, baseMoneyTwo,
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusMinutes(3).toInstant()));

        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositionsNoBond(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, baseMoneyThree,
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusMinutes(2).toInstant()));
    }



    List<SlavePortfolio.Position> oneSlavePositions(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal("20"))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }


    List<SlavePortfolio.Position> twoSlavePositionsNoBond(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("4862.8"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }





}