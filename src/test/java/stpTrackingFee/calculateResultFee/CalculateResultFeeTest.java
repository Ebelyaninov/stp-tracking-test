package stpTrackingFee.calculateResultFee;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.*;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.ResultFeeDao;
import ru.qa.tinkoff.investTracking.services.SlaveAdjustDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.SptTrackingFeeStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingFeeSteps.StpTrackingFeeSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.google.common.collect.Comparators.max;
import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.*;


@Slf4j
@Epic("calculateResultFee - Расчет комиссии за результат")
@Feature("TAP-9903")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-fee")
@Tags({@Tag("stp-tracking-fee"), @Tag("calculateResultFee")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SptTrackingFeeStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})
public class CalculateResultFeeTest {
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
    StpTrackingSlaveSteps slaveSteps;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    StringToByteSenderService kafkaStringToByteSender;
    @Autowired
    ResultFeeDao resultFeeDao;
    @Autowired
    SlaveAdjustDao slaveAdjustDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    ApiCreator<SubscriptionApi> subscriptionApiCreator;

    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();



    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Subscription subscription;
    SlavePortfolio slavePortfolio;
    ResultFee resultFee;
    UUID strategyId;
    UUID investIdMaster;
    UUID investIdSlave;
    SlaveAdjust slaveAdjust;
    String operId = "2321010121";
    String siebelIdMaster;
    String siebelIdSlave;

    String quantitySBER = "20";
    String quantitySU29009RMFS6 = "5";
    String quantityYNDX = "5";

    BigDecimal minPriceIncrement = new BigDecimal("0.001");


    String description = "new autotest CalculateResultFeeTest";

    @BeforeAll
    void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingFee;
        siebelIdSlave = stpSiebel.siebelIdSlaveStpTrackingFee;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        step("Удаляем клиента автоследования", () -> {
            try {
                contractService.deleteContractById(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(investIdSlave);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContractById(contractIdMaster);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(investIdMaster);
            } catch (Exception e) {
            }
        });
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                slaveAdjustDao.deleteSlaveAdjustByStrategyAndContract(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscription.getId());
            } catch (Exception e) {
            }
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
                createEventInTrackingEvent(contractIdSlave);
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
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4).minusMinutes(5);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        //считаем первые HighWaterMark для первого расчетный период где high water mark еще не рассчитывался
        Date startedAt = Date.from(subscription.getStartTime().toInstant().atZone(UTC).toInstant());
        Date cutDate = Date.from(determineSettlementPeriods.get(1).atZone(ZoneId.systemDefault()).toInstant());
        BigDecimal highWaterMark = getHighWaterMarkPeriod(startedAt, cutDate, new BigDecimal("0"), valuePortfoliosList.get(0));
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 1; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i - 1), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //ожидаем записи в result_fee
        checkComparedToFeeVersion(versionsList.get(2), subscriptionId);
        //проверяем полученные данные
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i)));
        }
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
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //обавляем еще пару версий за текущий месяц
        List<SlavePortfolio.Position> positionSlaveListVersionTen = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", "285.51", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "8", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "8", "5031.4",
            Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTen = steps.createBaseMoney("31051.38",
            Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 10,
            3, baseMoneyVersionTen, positionSlaveListVersionTen, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        List<SlavePortfolio.Position> positionSlaveListVersionEleven = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", "285.51", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "8", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "8", "5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEleven = steps.createBaseMoney("34051.38",
            Date.from(OffsetDateTime.now().minusMonths(0).minusDays(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 11,
            3, baseMoneyVersionEleven, positionSlaveListVersionEleven, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(1).toInstant()));
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        //считаем первые HighWaterMark для первого расчетный период где high water mark еще не рассчитывался
        Date startedAt = Date.from(subscription.getStartTime().toInstant().atZone(UTC).toInstant());
        Date cutDate = Date.from(determineSettlementPeriods.get(1).atZone(ZoneId.systemDefault()).toInstant());
        BigDecimal highWaterMark = getHighWaterMarkPeriod(startedAt, cutDate, new BigDecimal("0"), valuePortfoliosList.get(0));
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 1; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i - 1), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(versionsList.get(2), subscriptionId);
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i)));
        }
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
        strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //добавляем записи в result_fee
        createFeeResultFirst(startSubTime, subscriptionId);
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        resultFee = resultFeeDao.getLastResultFee(contractIdSlave, strategyId, subscriptionId);
        BigDecimal highWaterMark = resultFee.getHighWaterMark();
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 0; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(versionsList.get(0), subscriptionId);
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i + 1)));
        }
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
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //обавляем еще пару версий за текущий месяц
        List<SlavePortfolio.Position> positionSlaveListVersionTen = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", "285.51", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "8", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "8", "5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTen = steps.createBaseMoney("31051.38",
            Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()), (byte) 12);

        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 10,
            3, baseMoneyVersionTen, positionSlaveListVersionTen, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(3).toInstant()));

        List<SlavePortfolio.Position> positionSlaveListVersionEleven = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", "285.51", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "8", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "8", "5031.4", Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEleven = steps.createBaseMoney("34051.38",
            Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 11,
            3, baseMoneyVersionEleven, positionSlaveListVersionEleven, Date.from(OffsetDateTime.now().minusMonths(0).minusDays(2).toInstant()));
        //добавляем записи в result_fee
        createTwoPeriodFeeResult(startSubTime, subscriptionId);
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        resultFee = resultFeeDao.getLastResultFee(contractIdSlave, strategyId, subscriptionId);
        BigDecimal highWaterMark = resultFee.getHighWaterMark();
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 0; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //ждем записи в табл. result_fee
        checkComparedToFeeVersion(versionsList.get(0), subscriptionId);
        //проверяем рассчитанные значения  полученные из result_fee
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i + 1)));
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1093785")
    @DisplayName("C1093785.CalculateResultFee.Формируем команду в топик tracking.fee.calculate.command")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1093785() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(5);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //перемещаем в топике tracking.fee.calculate.command Offset в конец очереди сообщений
        steps.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
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
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(5).atStartOfDay().minusHours(3).toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().minusHours(3).toString()));
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
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
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
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
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
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(UTC));
        Date endFirst = Date.from(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 3,
            startFirst, endFirst, context, new BigDecimal("65162.5"), endFirst);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        createCommandResult(subscriptionId);
        // проверяем, что команда в tracking.fee.calculate.command не улетает
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


    @SneakyThrows
    @Test
    @AllureId("1419019")
    @DisplayName("C1419019.CalculateResultFee.Расчет комиссии за результат. " +
        "Не найдена позиция в exchangePositionCache при расчете стоимости портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1419019() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney("50000.0",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()));
        List<SlavePortfolio.Position> positionListVersionFive = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", "285.51", instrument.tickerTEST, instrument.tradingClearingAccountTEST, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5", "5031.4", Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFive = steps.createBaseMoney("28606.35",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionFive, positionListVersionFive, Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20",
            "285.51", Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("442898",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            3, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(1, subscriptionId);
        Optional<ResultFee> portfolio = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 1);
        assertThat("запись по комисси не равно", portfolio.isPresent(), is(true));
        Optional<ResultFee> portfolio1 = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 2);
        assertThat("запись по комисси не равно", portfolio1.isPresent(), is(false));
        Optional<ResultFee> portfolio2 = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("запись по комисси не равно", portfolio2.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1419025")
    @DisplayName("C1419025.CalculateResultFee.Расчет комиссии за результат. " +
        "Не найдена позиция в exchangePositionCache при расчете стоимости портфеля instrumentPriceCache")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1419025() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);

        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney("50000.0",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()));

        List<SlavePortfolio.Position> positionListVersionFive = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", "285.51", instrument.tickerFXITTEST, instrument.tradingClearingAccountFXITTEST, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5", "5031.4", Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFive = steps.createBaseMoney("28606.35",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionFive, positionListVersionFive, Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20",
            "285.51", Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("442898",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            3, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(1, subscriptionId);
        Optional<ResultFee> portfolio = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 1);
        assertThat("запись по комисси не равно", portfolio.isPresent(), is(true));
        Optional<ResultFee> portfolio1 = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 2);
        assertThat("запись по комисси не равно", portfolio1.isPresent(), is(false));
        Optional<ResultFee> portfolio2 = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("запись по комисси не равно", portfolio2.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1494295")
    @DisplayName("C1494295.CalculateResultFee.Расчет комиссии за результат. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt <= метки времени от now(), по cron-выражению.ContractBlocked")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1494295() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, true, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        Optional<ResultFee> portfolio = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 4);
        assertThat("запись по комисси не равно", portfolio.isPresent(), is(false));
        Optional<ResultFee> portfolio1 = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 7);
        assertThat("запись по комисси не равно", portfolio1.isPresent(), is(false));
        Optional<ResultFee> portfolio2 = resultFeeDao.findLastResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        assertThat("запись по комисси не равно", portfolio2.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1494359")
    @DisplayName("C1494359.CalculateResultFee.Расчет комиссии за результат. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt <= метки времени от now(), по cron-выражению." +
        "Расчет пропущенного периода")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1494359() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePortfolioWithOutPeriod();
        //добавляем записи в result_fee
        createFeeResultFirst(startSubTime, subscriptionId);

        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        resultFee = resultFeeDao.getLastResultFee(contractIdSlave, strategyId, subscriptionId);
        BigDecimal highWaterMark = resultFee.getHighWaterMark();
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 0; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(versionsList.get(1), subscriptionId);
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i + 1)));
        }

    }


    @SneakyThrows
    @Test
    @AllureId("1482950")
    @DisplayName("C1482950. Расчет первой комиссии за результат с отрицательной BaseMoneyPosition и пустым портфелем, нет заводов")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1482950() {
        BigDecimal baseMoneyPositionForFirstVersion = new BigDecimal("-53763.35");
        BigDecimal baseMoneyPositionForLastVersion = new BigDecimal("53763.35");
        strategyId = UUID.fromString("070d11e3-9978-4c18-9316-94daa877b641");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1);
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(1).toInstant()));
        List<SlavePortfolio.Position> positionListVersionThree = twoSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney(baseMoneyPositionForLastVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionFour, positionListVersionThree, Date.from(OffsetDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).toInstant()));
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(1, subscriptionId);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 1);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(BigDecimal.valueOf(0)));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(BigDecimal.valueOf(0)));
        //Проверяем отправку HWM в событии 0
        assertThat("subscriptionId подписки не равен", feeCommand.getResult().getHighWaterMark().getScale(), is(0));
        assertThat("subscriptionId подписки не равен", feeCommand.getResult().getHighWaterMark().getUnscaled(), is(0L));
    }


    @SneakyThrows
    @Test
    @AllureId("1488412")
    @DisplayName("C1488412. Расчет первой комиссии за результат с отрицательным портфелем на старте и переопределение HWM текущем портфелем")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1488412() {
        BigDecimal baseMoneyPositionForFirstVersion = new BigDecimal("-153763.35");
        BigDecimal baseMoneyPositionForLastVersion = new BigDecimal("13289.21");
        strategyId = UUID.fromString("070d11e3-9978-4c18-9316-94daa877b641");
        BigDecimal slaveAdjustValue = new BigDecimal("5000");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1);
        LocalDateTime dateOfEndFirstPeriod = LocalDateTime.now().with(LocalTime.MIN).minusMonths(0).with(TemporalAdjusters.firstDayOfMonth());
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(1).toInstant()));
        List<SlavePortfolio.Position> positionListVersionTwo = twoSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "5",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney(baseMoneyPositionForLastVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionFour, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(2).toInstant()));
        //Добавляем заводы на сумму baseMoneyPositionForFirstVersion + baseMoneyPositionForLastVersion
        createsSlaveAdjust(contractIdSlave, strategyId, OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), Long.parseLong(operId),
            OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), "rub", false, slaveAdjustValue.toString());
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        //считаем первые HighWaterMark первый расчетный период и high water mark еще не рассчитывался
        Date startedAt = Date.from(subscription.getStartTime().toInstant().atZone(UTC).toInstant());
        Date cutDate = Date.from(determineSettlementPeriods.get(1).atZone(ZoneId.systemDefault()).toInstant());
        BigDecimal highWaterMark = getHighWaterMarkPeriod(startedAt, cutDate, new BigDecimal("0"), valuePortfoliosList.get(0));
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 1; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i - 1), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(versionsList.get(0), subscriptionId);
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i)));
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1483238")
    @DisplayName("C1483238. Стоимость портфеля отрицательная, для первого HWM c пустым портфелем и заводами")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1483238() {
        BigDecimal baseMoneyPositionForFirstVersion = new BigDecimal("-23763.35");
        BigDecimal slaveAdjustValueFirst = new BigDecimal("5000");
        BigDecimal slaveAdjustValueSecond = new BigDecimal("5000");
        strategyId = UUID.fromString("070d11e3-9978-4c18-9316-94daa877b641");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();

        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(1).toInstant()));

        //Добавляем заводы на сумму 10000
        createsSlaveAdjust(contractIdSlave, strategyId, OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), Long.parseLong(operId),
            OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), "rub", false, slaveAdjustValueFirst.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), Long.parseLong(operId),
            OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), "rub", false, slaveAdjustValueSecond.toString());

        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofSeconds(1)).until(() ->
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 1), notNullValue());

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);

        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(BigDecimal.valueOf(0)));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(slaveAdjustValueFirst.add(slaveAdjustValueSecond)));
        //Проверяем отправку HWM в событии 0 + заводы
        assertThat("subscriptionId подписки не равен", feeCommand.getResult().getHighWaterMark().getScale(), is(0));
        assertThat("subscriptionId подписки не равен", feeCommand.getResult().getHighWaterMark().getUnscaled(), is(Long.valueOf(slaveAdjustValueFirst.add(slaveAdjustValueSecond).toString())));
    }


    @SneakyThrows
    @Test
    @AllureId("1081005")
    @DisplayName("С1488560. Расчет второго HWM с отрицательным портфелем + заводы")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1488560() {
        BigDecimal baseMoneyPositionForFirstVersion = new BigDecimal("-123763.35");
        BigDecimal baseMoneyPositionForLastVersion = new BigDecimal("53763.35");
        BigDecimal hWM = new BigDecimal("66319.95000");
        BigDecimal slaveAdjustValue = new BigDecimal("5000");
        strategyId = UUID.fromString("070d11e3-9978-4c18-9316-94daa877b641");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(2);
        LocalDateTime dateOfEndFirstPeriod = LocalDateTime.now().with(LocalTime.MIN).minusMonths(0).with(TemporalAdjusters.firstDayOfMonth());
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).plusMinutes(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).plusMinutes(1).toInstant()));
        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20",
            "285.51", Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("44898",
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).plusMinutes(2).plusMinutes(2).toInstant()));
        List<SlavePortfolio.Position> positionListVersionThree = twoSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "5",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionThree = steps.createBaseMoney("43763.35",
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            2, baseMoneyVersionThree, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(3).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney(baseMoneyPositionForLastVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 4,
            3, baseMoneyVersionFour, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(4).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFive = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 5,
            4, baseMoneyVersionFive, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5).toInstant()));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(Date.from(startSubTime.toInstant()))
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(Date.from(startSubTime.toInstant()))
            .build());
        Context context = Context.builder()
            .portfolioValue(hWM)
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 4,
            Date.from(startSubTime.toInstant()), Date.from(dateOfEndFirstPeriod.toInstant(UTC)), context, hWM, Date.from(dateOfEndFirstPeriod.toInstant(UTC)));
        //Добавляем заводы на сумму baseMoneyPositionForFirstVersion + baseMoneyPositionForLastVersion
        createsSlaveAdjust(contractIdSlave, strategyId, OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), Long.parseLong(operId),
            OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5), "rub", false, slaveAdjustValue.toString());

        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //Расчитываем стоимость порфеля на конец первого расчетного периода
        BigDecimal valueOfFirstPosition = calculateNotBondPositionValue(dateOfEndFirstPeriod, BigDecimal.valueOf(20), instrument.tickerSBER, instrument.classCodeSBER);
        BigDecimal valueOfSecondPosition = calculatePositionBondValue(dateOfEndFirstPeriod, BigDecimal.valueOf(5), instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6);
        //c отрицательным BaseMoneyPosition
        BigDecimal porfolioValue = valueOfFirstPosition.add(valueOfSecondPosition).add(baseMoneyPositionForFirstVersion);

        if (porfolioValue.compareTo(BigDecimal.ZERO) < 0) {
            porfolioValue = new BigDecimal("0");
        }
        checkComparedToFeeVersion(5, subscriptionId);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 5);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(porfolioValue));
        //Переопределяем HWM, сумма заводов + HWM
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(hWM.add(slaveAdjustValue)));
    }


    @SneakyThrows
    @Test
    @AllureId("1081005")
    @DisplayName("Расчет второго HWM c положительным портфелем и отрицательным BaseMoneyPosition")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1488561() {
        BigDecimal baseMoneyPositionForFirstVersion = new BigDecimal("-9763.35");
        BigDecimal baseMoneyPositionForLastVersion = new BigDecimal("53763.35");
        BigDecimal hWM = new BigDecimal("112.2");
        strategyId = UUID.fromString("070d11e3-9978-4c18-9316-94daa877b641");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(2);
        LocalDateTime dateOfEndFirstPeriod = LocalDateTime.now().with(LocalTime.MIN).minusMonths(0).with(TemporalAdjusters.firstDayOfMonth());
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).plusMinutes(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).plusMinutes(1).toInstant()));
        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20",
            "285.51", Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("44898",
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).plusMinutes(2).plusMinutes(2).toInstant()));
        List<SlavePortfolio.Position> positionListVersionThree = twoSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "15",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(2).plusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionThree = steps.createBaseMoney("43763.35",
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            2, baseMoneyVersionThree, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(3).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney(baseMoneyPositionForLastVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 4,
            3, baseMoneyVersionFour, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(4).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFive = steps.createBaseMoney(baseMoneyPositionForFirstVersion.toString(),
            Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 5,
            4, baseMoneyVersionFive, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(1).plusDays(1).plusMinutes(5).toInstant()));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(Date.from(startSubTime.toInstant()))
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(Date.from(startSubTime.toInstant()))
            .build());
        Context context = Context.builder()
            .portfolioValue(hWM)
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 4,
            Date.from(startSubTime.toInstant()), Date.from(dateOfEndFirstPeriod.toInstant(UTC)), context, hWM, Date.from(dateOfEndFirstPeriod.toInstant(UTC)));

        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        resultFee = resultFeeDao.getLastResultFee(contractIdSlave, strategyId, subscriptionId);
        BigDecimal highWaterMark = resultFee.getHighWaterMark();
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 0; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //ждем появления записи в табл. result_fee
        checkComparedToFeeVersion(versionsList.get(0), subscriptionId);
        //проверяем расчеты и данные из result_fee
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i + 1)));
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1443477")
    @DisplayName("C1443477.CalculateResultFee. Нашли все записи в slave_adjust с базовой валютой стратегии rub")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1443477() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);

        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //добавляем записи в result_fee
        createFeeResultFirst(startSubTime, subscriptionId);
        //Расчитываем стоимость порфеля на конец второго расчетного периода
        LocalDateTime lastDayFirstSecondPeriod = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioSecondPeriod = getPorfolioValue("43606.35", "20", "5", "5", lastDayFirstSecondPeriod);
        LocalDateTime lastDayFirstThirdPeriod = LocalDate.now().minusMonths(0).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioThirdPeriod = getPorfolioValue("31367.25", "10", "5", "8", lastDayFirstThirdPeriod);
        BigDecimal highWaterMarkFirstPeriod = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 4).getHighWaterMark();
        BigDecimal highWaterMarkSecondPeriod;
        BigDecimal highWaterMarkThirdPeriod;
        //Добавляем заводы RUB
        //игнорируем завод за первый период
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusDays(1), Long.parseLong(operId),
            startSubTime.plusDays(1), "rub", false, "1500000.21");
        //Добавляем несколько заводов за 2 период > чем portfolioValue
        BigDecimal firstAdjustForSecondPeriod = new BigDecimal("15021.24");
        BigDecimal secondAdjustForSecondPeriod = new BigDecimal("16021.33");
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 1,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), "rub", false, firstAdjustForSecondPeriod.toString());
        //Получаем новый HWM (HWM уже рассчитаного первого периода + заводы)
        highWaterMarkSecondPeriod = highWaterMarkFirstPeriod.add(firstAdjustForSecondPeriod).add(secondAdjustForSecondPeriod);
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 2,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), "rub", false, secondAdjustForSecondPeriod.toString());
        //Добавляем несколько заводов за 3 период > чем portfolioValue
        BigDecimal firstAdjustForThirdPeriod = new BigDecimal("25021.24");
        BigDecimal secondAdjustForThirdPeriod = new BigDecimal("26021.33");
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 3,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), "rub", false, firstAdjustForThirdPeriod.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 3,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), "rub", false, secondAdjustForThirdPeriod.toString());
        //Получаем новый HWM (HWM уже рассчитаного второго периода + заводы)
        highWaterMarkThirdPeriod = highWaterMarkSecondPeriod.add(firstAdjustForThirdPeriod).add(secondAdjustForThirdPeriod);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofSeconds(1))
            .until(
                () -> resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7),
                notNullValue());
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioSecondPeriod));
        //К рассчитаному HWM за 2 период добавляем сумму 2 заводов
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkSecondPeriod));
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioThirdPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkThirdPeriod));
    }


    @SneakyThrows
    @Test
    @AllureId("1442125")
    @DisplayName("C1442125.CalculateResultFee. Не нашли записи в таблице slave_adjust если уже была найдена запись в result_fee")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1442125() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);

        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //добавляем записи в result_fee
        createFeeResultFirst(startSubTime, subscriptionId);
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        resultFee = resultFeeDao.getLastResultFee(contractIdSlave, strategyId, subscriptionId);
        BigDecimal highWaterMark = resultFee.getHighWaterMark();
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 0; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        checkComparedToFeeVersion(versionsList.get(1), subscriptionId);
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i + 1)));
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1443460")
    @DisplayName("C1443460.CalculateResultFee. Отфильтровываем записи у которых у которых slave_adjust.currency = strategy.base_currency")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1443460() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //добавляем записи в result_fee
        createFeeResultFirst(startSubTime, subscriptionId);
        //Расчитываем стоимость порфеля на конец второго расчетного периода
        LocalDateTime lastDayFirstSecondPeriod = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioSecondPeriod = getPorfolioValue("43606.35", "20", "5", "5", lastDayFirstSecondPeriod);
        LocalDateTime lastDayFirstThirdPeriod = LocalDate.now().minusMonths(0).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioThirdPeriod = getPorfolioValue("31367.25", "10", "5", "8", lastDayFirstThirdPeriod);
        BigDecimal highWaterMarkFirstPeriod = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 4).getHighWaterMark();
        BigDecimal highWaterMarkSecondPeriod;
        BigDecimal highWaterMarkThirdPeriod;
        //Добавляем заводы RUB
        //Добавляем несколько заводов за 2 период > чем portfolioValue
        BigDecimal firstAdjustForSecondPeriod = new BigDecimal("15021.24");
        BigDecimal secondAdjustForSecondPeriod = new BigDecimal("16021.33");
        BigDecimal adjustForUsd = new BigDecimal("16021.33");
        BigDecimal adjustForEur = new BigDecimal("26021.33");
        //Добавляем завод в другой валюте
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 1,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), "usd", false, firstAdjustForSecondPeriod.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 2,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), "usd", false, secondAdjustForSecondPeriod.toString());
        //Добавить заводы в валюте != базовая валюта и не rub
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 3,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(3), "rub", false, adjustForUsd.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 4,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(4), "eur", false, adjustForEur.toString());
        //Получаем новый HWM (HWM уже рассчитаного первого периода + заводы только базовой валюты)
        highWaterMarkSecondPeriod = highWaterMarkFirstPeriod.add(firstAdjustForSecondPeriod).add(secondAdjustForSecondPeriod);
        //Добавляем несколько заводов за 3 период > чем portfolioValue
        BigDecimal firstAdjustForThirdPeriod = new BigDecimal("25021.24");
        BigDecimal secondAdjustForThirdPeriod = new BigDecimal("26021.33");
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 5,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), "rub", false, firstAdjustForThirdPeriod.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 6,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), "eur", false, secondAdjustForThirdPeriod.toString());
        //Получаем новый HWM (HWM уже рассчитаного второго периода + заводы игнорируем)
        BigDecimal highWaterMarkThirdPeriodBefore = highWaterMarkSecondPeriod;
        highWaterMarkThirdPeriod = highWaterMarkThirdPeriodBefore.max(valuePortfolioThirdPeriod);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);

//        await().atMost(Duration.ofSeconds(5))
//            .until(
//                () -> resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7),
//                notNullValue());
        checkComparedToFeeVersion(7, subscriptionId);
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 7);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioSecondPeriod));
        //К рассчитаному HWM за 2 период добавляем сумму 2 заводов
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkSecondPeriod));
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9);
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolioThirdPeriod));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMarkThirdPeriod));
    }


    @SneakyThrows
    @Test
    @AllureId("1451384")
    @DisplayName("C1451384.CalculateResultFee. Первый расчетный период и high water mark еще не рассчитывался")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1451384() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //Расчитываем стоимость порфеля на конец второго расчетного периода
        LocalDateTime lastDayFirstSecondPeriod = LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioSecondPeriod = getPorfolioValue("43606.35", "20", "5", "5", lastDayFirstSecondPeriod);
        LocalDateTime lastDayFirstThirdPeriod = LocalDate.now().minusMonths(0).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        BigDecimal valuePortfolioThirdPeriod = getPorfolioValue("31367.25", "10", "5", "8", lastDayFirstThirdPeriod);
        BigDecimal highWaterMarkFirstPeriod;
        BigDecimal highWaterMarkSecondPeriod;
        BigDecimal highWaterMarkThirdPeriod;
        //Добавляем заводы RUB
        BigDecimal firstAdjustForFirstPeriod = new BigDecimal("28021.24");
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusDays(1), Long.parseLong(operId),
            startSubTime.plusDays(1), "rub", false, firstAdjustForFirstPeriod.toString());
        //Получаем новый HWM (HWM уже рассчитаного первого периода + заводы)
        BigDecimal valuePortfolioOnePeriod = createPortfolioValueOnePeriod();
        BigDecimal highWaterMarkForFirstPortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 1).getBaseMoneyPosition().getQuantity();
        highWaterMarkFirstPeriod = valuePortfolioOnePeriod.max(highWaterMarkForFirstPortfolio.add(firstAdjustForFirstPeriod));
        //Добавляем несколько заводов за 2 период > чем portfolioValue
        BigDecimal firstAdjustForSecondPeriod = new BigDecimal("15021.24");
        BigDecimal secondAdjustForSecondPeriod = new BigDecimal("16021.33");
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 1,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), "rub", false, firstAdjustForSecondPeriod.toString());
        //Получаем новый HWM (HWM уже рассчитаного первого периода + заводы)
        highWaterMarkSecondPeriod = highWaterMarkFirstPeriod.add(firstAdjustForSecondPeriod).add(secondAdjustForSecondPeriod);
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 2,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), "rub", false, secondAdjustForSecondPeriod.toString());
        //Добавляем несколько заводов за 3 период > чем portfolioValue
        BigDecimal firstAdjustForThirdPeriod = new BigDecimal("25021.24");
        BigDecimal secondAdjustForThirdPeriod = new BigDecimal("26021.33");
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 3,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), "rub", false, firstAdjustForThirdPeriod.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 3,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), "rub", false, secondAdjustForThirdPeriod.toString());
        //Получаем новый HWM (HWM уже рассчитаного второго периода + заводы)
        highWaterMarkThirdPeriod = highWaterMarkSecondPeriod.add(firstAdjustForThirdPeriod).add(secondAdjustForThirdPeriod);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(500))
            .until(
                () -> resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 9),
                notNullValue());
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
    @AllureId("1443488")
    @DisplayName("C1443488.CalculateResultFee. Перевод валюты заводов в рубли (strategy.base_currency = 'rub' И у какой-то из найденных операций завода currency <> 'rub')")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1443488() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(4);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlaveportfolio();
        //добавляем записи в result_fee
        createFeeResultFirst(startSubTime, subscriptionId);
        //Добавляем несколько заводов за 2 период > чем portfolioValue
        BigDecimal adjustForUsd = new BigDecimal("1024.33");
        BigDecimal adjustForEur = new BigDecimal("893.33");
        //Добавляем завод в валюте отличной от rub
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 1,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(1), "usd", false, adjustForUsd.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 2,
            startSubTime.plusMonths(1).withDayOfMonth(1).plusDays(2), "eur", false, adjustForEur.toString());
        //Добавляем несколько заводов за 3 период > чем portfolioValue
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), Long.parseLong(operId) + 3,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(1), "eur", false, adjustForEur.toString());
        createsSlaveAdjust(contractIdSlave, strategyId, startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), Long.parseLong(operId) + 4,
            startSubTime.plusMonths(2).withDayOfMonth(1).plusDays(2), "usd", false, adjustForUsd.toString());
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        //получаем списки стоимостей портфелей версии и  на конец  каждого расчетного периода
        List<BigDecimal> valuePortfoliosList = new ArrayList<>();
        List<Integer> versionsList = new ArrayList<>();
        //проходим каждый расчетный период из determineSettlementPeriods,
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        for (int i = 1; i < determineSettlementPeriods.size(); i++) {
            BigDecimal valuePortfolio = BigDecimal.ZERO;
            int version;
            Date cutDate = Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant());
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
            if (slavePortfolio != null) {
                valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(i));
                valuePortfoliosList.add(valuePortfolio);
                version = slavePortfolio.getVersion();
                versionsList.add(version);
            }
        }
        //считаем HighWaterMark и сохраняем для каждого расчетого приода в список
        List<BigDecimal> valueHighWaterMarkList = new ArrayList<>();
        resultFee = resultFeeDao.getLastResultFee(contractIdSlave, strategyId, subscriptionId);
        BigDecimal highWaterMark = resultFee.getHighWaterMark();
        valueHighWaterMarkList.add(highWaterMark);
        //если high water mark уже рассчитывался, то выбираем последний рассчитанный result_fee.high_water_mark
        for (int i = 0; i < determineSettlementPeriods.size() - 1; i++) {
            highWaterMark = getHighWaterMarkPeriod(Date.from(determineSettlementPeriods.get(i).atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(determineSettlementPeriods.get(i + 1).atZone(ZoneId.systemDefault()).toInstant()),
                valueHighWaterMarkList.get(i), valuePortfoliosList.get(i));
            valueHighWaterMarkList.add(highWaterMark);
        }
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //ждем появления записи в табл. result_fee
        checkComparedToFeeVersion(versionsList.get(1), subscriptionId);
        for (int i = 0; i < versionsList.size(); i++) {
            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, versionsList.get(i));
            assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfoliosList.get(i)));
            assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(valueHighWaterMarkList.get(i + 1)));
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1696489")
    @DisplayName("C1696489.CalculateResultFee Не нашли портфель во время расчета комиссии за результат")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1696489() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        steps.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //ожидаем записи в result_fee
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofSeconds(2)).until(() ->
            resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 0), notNullValue());
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 0);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //Проверяем отправку нулевой комисси
        assertThat("HWM не равен 0", feeCommand.getResult().getHighWaterMark().getScale(), is(0));
        assertThat("HWM не равен 0", feeCommand.getResult().getHighWaterMark().getUnscaled(), is(0L));
        assertThat("portfolio_value не равен 0", feeCommand.getResult().getPortfolioValue().getScale(), is(0));
        assertThat("portfolio_value не равен 0", feeCommand.getResult().getPortfolioValue().getUnscaled(), is(0L));
        //проверяем комисию
        assertThat("version комиссии не равно", resultFee.getVersion(), is(0));
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(new BigDecimal("0")));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(new BigDecimal("0")));
    }



    @SneakyThrows
    @Test
    @AllureId("1626800")
    @DisplayName("1626800 CalculateResultFee.Добавление метки времени created_at. created_at = now () utc")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1626800() {
        strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(40));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "40",
            instrument.tickerFB, instrument.tradingClearingAccountFB, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(40);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаём портфели slave
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, "17564",
            positionList, Date.from(OffsetDateTime.now().minusDays(40).toInstant()));
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommandResult(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        checkComparedToFeeVersion(1, subscriptionId);
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //проверяем полученное событие в tracking.fee.calculate.command
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("portfolioValue не равен", feeCommand.getManagement().getPortfolioValue().getScale(), is(0));
        //проверяем запись в таблице management_fee
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 1);
        assertThat("contractID не равен", resultFee.getContractId(), is(contractIdSlave));
        assertThat("strategyID не равен", resultFee.getStrategyId(), is(strategyId));
        assertThat("Version не равен", resultFee.getVersion(), is(1));
        assertThat("created_at не равен", resultFee.getCreatedAt().toInstant().getEpochSecond(), is(feeCommand.getCreatedAt().getSeconds()));
    }


    @SneakyThrows
    @Test
    @AllureId("1626799")
    @DisplayName("1626799 CalculateResultFee.Добавление метки времени created_at, после отписки от стратегии.")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1626799() {
        strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "40",
            instrument.tickerFB, instrument.tradingClearingAccountFB, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription1(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаём портфели slave
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, "17564",
            positionList, Date.from(OffsetDateTime.now().minusDays(40).toInstant()));
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //отписываемся от стратегии
        subscriptionApiCreator.get().deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //получаем подписку
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        checkComparedToFeeVersion(1, subscriptionId);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //проверяем полученное событие в tracking.fee.calculate.command
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("portfolioValue не равен", feeCommand.getManagement().getPortfolioValue().getScale(), is(0));
        checkComparedToFeeVersion(1, subscriptionId);
        //проверяем запись в таблице management_fee
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, 1);
        assertThat("contractID не равен", resultFee.getContractId(), is(contractIdSlave));
        assertThat("strategyID не равен", resultFee.getStrategyId(), is(strategyId));
        assertThat("Version не равен", resultFee.getVersion(), is(1));
        assertThat("created_at не равен", resultFee.getCreatedAt().toInstant().getEpochSecond(), is(feeCommand.getCreatedAt().getSeconds()));
    }



    @SneakyThrows
    @Test
    @AllureId("1834088")
    @DisplayName("1834088 CalculateResultFee. Первый расчетный период. Блокировка subscription по minimum_value.")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1834088() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(3).minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1).minusDays(4).minusMinutes(5);
        steps.createSubcriptionWithBlock(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, 2);
        //получаем идентификатор подписки
        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdSlave).getId();
        //добавляем запись о блокировке в subscription_block
        LocalDate startBlock = (LocalDate.now().minusMonths(1).minusDays(1));
        LocalDate endBlock = (LocalDate.now().minusDays(15));
        String periodDefault = "[" + startBlock + "," + endBlock + ")";
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefault, 2);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем портфели slave
        //Портфель 1 версии
        OffsetDateTime utcStart = OffsetDateTime.now().minusMonths(1).minusDays(10);
        Date dateStart = Date.from(utcStart.toInstant());
        String baseMoneySlave = "6657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, dateStart, positionList);
        //Портфель 2 версии
        OffsetDateTime utcStart1 = OffsetDateTime.now().minusDays(15);
        Date dateStart1 = Date.from(utcStart1.toInstant());
        String baseMoneySlave1 = "7657.23";
        List<SlavePortfolio.Position> positionList1 = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave1, dateStart1, positionList1);
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        BigDecimal valuePortfolio = BigDecimal.ZERO;
        Date cutDate = Date.from(determineSettlementPeriods.get(0).atZone(ZoneId.systemDefault()).toInstant());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioAfter(contractIdSlave, strategyId, cutDate);
        if (slavePortfolio != null) {
            valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(0));
        }
        //считаем первый HighWaterMark для первого расчетного периода, где high water mark еще не рассчитывался
        Date endedAt = java.sql.Date.valueOf(endBlock);
        Date cutDate1 = Date.from(determineSettlementPeriods.get(0).atZone(ZoneId.systemDefault()).toInstant());
        BigDecimal highWaterMark = getHighWaterMarkPeriod(endedAt, cutDate1, new BigDecimal("0"), valuePortfolio);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //ожидаем записи в result_fee
        checkComparedToFeeVersion(slavePortfolio.getVersion(), subscriptionId);
        //проверяем полученные данные
        resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, slavePortfolio.getVersion());
        assertThat("value стоимости портфеля не равно", resultFee.getContext().getPortfolioValue(), is(valuePortfolio));
        assertThat("high_water_mark не равно", resultFee.getHighWaterMark(), is(highWaterMark));
        assertThat("settlement_period_started не равен окончанию блокировки", resultFee.getSettlementPeriodStartedAt(), is(endedAt));

    }


    @SneakyThrows
    @Test
    @AllureId("1836420")
    @DisplayName("1836420 ResultFee. Первый расчетный период. Блокировка subscription по minimum_value. slave_portfolio.version != subscription_block.version.")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1836420() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(3).minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1).minusDays(4).minusMinutes(5);
        steps.createSubcriptionWithBlock(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, 2);
        //получаем идентификатор подписки
        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdSlave).getId();
        //добавляем запись о блокировке в subscription_block
        LocalDate startBlock = (LocalDate.now().minusMonths(1).minusDays(1));
        LocalDate endBlock = (LocalDate.now().minusDays(15));
        String periodDefault = "[" + startBlock + "," + endBlock + ")";
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefault, 3);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем портфели slave
        //Портфель 1 версии
        OffsetDateTime utcStart = OffsetDateTime.now().minusMonths(1).minusDays(10);
        Date dateStart = Date.from(utcStart.toInstant());
        String baseMoneySlave = "6657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, dateStart, positionList);
        //Портфель 2 версии
        OffsetDateTime utcStart1 = OffsetDateTime.now().minusDays(15);
        Date dateStart1 = Date.from(utcStart1.toInstant());
        String baseMoneySlave1 = "7657.23";
        List<SlavePortfolio.Position> positionList1 = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave1, dateStart1, positionList1);
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        BigDecimal valuePortfolio = BigDecimal.ZERO;
        Date cutDate = Date.from(determineSettlementPeriods.get(0).atZone(ZoneId.systemDefault()).toInstant());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioAfter(contractIdSlave, strategyId, cutDate);
        if (slavePortfolio != null) {
            valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(0));
        }
        //считаем первый HighWaterMark для первого расчетного периода, где high water mark еще не рассчитывался
        Date endedAt = java.sql.Date.valueOf(endBlock);
        Date cutDate1 = Date.from(determineSettlementPeriods.get(0).atZone(ZoneId.systemDefault()).toInstant());
        BigDecimal highWaterMark = getHighWaterMarkPeriod(endedAt, cutDate1, new BigDecimal("0"), valuePortfolio);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //проверяем полученные данные
        assertThat("появилась запись о начисленной комиссии", resultFeeDao.findResultFee(contractIdSlave, strategyId, subscriptionId, slavePortfolio.getVersion()), is(Optional.empty()));

    }


    @SneakyThrows
    @Test
    @AllureId("1836377")
    @DisplayName("1836377 ResultFee. Первый расчетный период. Блокировка subscription по minimum_value. version = null.")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1836377() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(3).minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1).minusDays(4).minusMinutes(5);
        steps.createSubcriptionWithBlock(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, 2);
        //получаем идентификатор подписки
        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdSlave).getId();
        //добавляем запись о блокировке в subscription_block
        LocalDate startBlock = (LocalDate.now().minusMonths(1).minusDays(1));
        LocalDate endBlock = (LocalDate.now().minusDays(15));
        String periodDefault = "[" + startBlock + "," + endBlock + ")";
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefault, null);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем портфели slave
        //Портфель 1 версии
        OffsetDateTime utcStart = OffsetDateTime.now().minusMonths(1).minusDays(10);
        Date dateStart = Date.from(utcStart.toInstant());
        String baseMoneySlave = "6657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, dateStart, positionList);
        //Портфель 2 версии
        OffsetDateTime utcStart1 = OffsetDateTime.now().minusDays(15);
        Date dateStart1 = Date.from(utcStart1.toInstant());
        String baseMoneySlave1 = "7657.23";
        List<SlavePortfolio.Position> positionList1 = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave1, dateStart1, positionList1);
        //получаем расчетные периоды
        List<LocalDateTime> determineSettlementPeriods = new ArrayList<>();
        determineSettlementPeriods = getDetermineSettlementPeriods(contractIdSlave, strategyId, subscriptionId);
        // достаем из БД портфель, рассчитываем его стоимость и сохранем версию
        BigDecimal valuePortfolio = BigDecimal.ZERO;
        Date cutDate = Date.from(determineSettlementPeriods.get(0).atZone(ZoneId.systemDefault()).toInstant());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioAfter(contractIdSlave, strategyId, cutDate);
        if (slavePortfolio != null) {
            valuePortfolio = getPortfolioValuePeriod(slavePortfolio, determineSettlementPeriods.get(0));
        }
        //считаем первый HighWaterMark для первого расчетного периода, где high water mark еще не рассчитывался
        Date endedAt = java.sql.Date.valueOf(endBlock);
        Date cutDate1 = Date.from(determineSettlementPeriods.get(0).atZone(ZoneId.systemDefault()).toInstant());
        BigDecimal highWaterMark = getHighWaterMarkPeriod(endedAt, cutDate1, new BigDecimal("0"), valuePortfolio);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //проверяем полученные данные
        assertThat("появилась запись о начисленной комиссии", resultFeeDao.findResultFee(contractIdSlave, strategyId, subscriptionId, slavePortfolio.getVersion()), is(Optional.empty()));

    }



    @SneakyThrows
    @Test
    @AllureId("1836261")
    @DisplayName("1836261 ResultFee. Первый расчетный период. Блокировка subscription по minimum_value. Блокировка активна. Возвращаем пустой started_at.")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1836261() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMonths(3).minusDays(3));
        OffsetDateTime utc = OffsetDateTime.now().minusMonths(3).minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6,
            instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(1).minusDays(10).minusMinutes(5);
        steps.createSubcriptionWithBlock(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, 2);
        //получаем идентификатор подписки
        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdSlave).getId();
        //добавляем запись о блокировке в subscription_block
        LocalDate startBlock = (LocalDate.now().minusMonths(1).minusDays(1));
        //LocalDate endBlock = (LocalDate.now().minusDays(15));
        String periodDefault = "[" + startBlock  + ",)";
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefault, 2);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //создаем портфели slave
        //Портфель 1 версии
        OffsetDateTime utcStart = OffsetDateTime.now().minusMonths(1).minusDays(4);
        Date dateStart = Date.from(utcStart.toInstant());
        String baseMoneySlave = "6657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, dateStart, positionList);
        //Портфель 2 версии
        OffsetDateTime utcStart1 = OffsetDateTime.now().minusDays(15);
        Date dateStart1 = Date.from(utcStart1.toInstant());
        String baseMoneySlave1 = "7657.23";
        List<SlavePortfolio.Position> positionList1 = new ArrayList<>();
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave1, dateStart1, positionList1);
        //формируем и отправляем команду на расчет комисии
        createCommandResult(subscriptionId);
        //проверяем полученные данные
        await().pollDelay(Duration.ofSeconds(3));
        assertThat("появилась запись о начисленной комиссии", resultFeeDao.findListResultFee(contractIdSlave, strategyId, subscriptionId).size(), is(0));

    }


    // методы для работы тестов*****************************************************************
    @Step("Отправляем команду в топик TRACKING_FEE_COMMAND: ")
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

    @Step("Создаем позиции для портфеля master: ")
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

    @Step("Создаем позиции для портфеля slave: ")
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

    @Step("Создаем позиции для портфеля slave: ")
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

    @Step("Создаем позиции для портфеля slave: ")
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

    @Step("Создаем записи по портфелю slave: ")
    void createSlaveportfolio() {
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney("50000.0",

            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(4).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(4).toInstant()));

        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20",
            "285.51", Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("442898",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()));

        List<SlavePortfolio.Position> positionListVersionThree = twoSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionThree = steps.createBaseMoney("43763.35",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            3, baseMoneyVersionThree, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()));


        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney("53763.35",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 4,
            3, baseMoneyVersionFour, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(0).toInstant()));


        List<SlavePortfolio.Position> positionListVersionFive = threeSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5", "5031.4", Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
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

        List<SlavePortfolio.Position> positionListVersionEight = threeSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "10", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5", "5031.4", Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEight = steps.createBaseMoney("46461.45",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(3).toInstant()), (byte) 11);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 8,
            3, baseMoneyVersionEight, positionListVersionEight, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));

        List<SlavePortfolio.Position> positionSlaveVersionNine = threeSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "10", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "8", "5031.4", Date.from(OffsetDateTime.now().minusMonths(1).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionNine = steps.createBaseMoney("31367.25",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 9,
            3, baseMoneyVersionNine, positionSlaveVersionNine, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(1).toInstant()));

    }

    @Step("Создаем записи по портфелю slave: ")
    void createSlavePortfolioWithOutPeriod() {
        List<SlavePortfolio.Position> positionListVersionOne = new ArrayList<>();
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionOne = steps.createBaseMoney("50000.0",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 1,
            1, baseMoneyVersionOne, positionListVersionOne, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(3).toInstant()));

        List<SlavePortfolio.Position> positionListVersionTwo = oneSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20",
            "285.51", Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionTwo = steps.createBaseMoney("442898",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 2,
            2, baseMoneyVersionTwo, positionListVersionTwo, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(2).toInstant()));

        List<SlavePortfolio.Position> positionListVersionThree = twoSlavePositions111(
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "20", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5",
            "105.29", Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionThree = steps.createBaseMoney("43763.35",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 3,
            3, baseMoneyVersionThree, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(1).toInstant()));

        SlavePortfolio.BaseMoneyPosition baseMoneyVersionFour = steps.createBaseMoney("53763.35",
            Date.from(OffsetDateTime.now().minusMonths(3).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 4,
            3, baseMoneyVersionFour, positionListVersionThree, Date.from(OffsetDateTime.now().minusMonths(3).minusDays(0).toInstant()));

        List<SlavePortfolio.Position> positionListVersionFive = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5", "5031.4",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));

        SlavePortfolio.BaseMoneyPosition baseMoneyVersionSeven = steps.createBaseMoney("43606.35",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(0).toInstant()), (byte) 4);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 7,
            3, baseMoneyVersionSeven, positionListVersionFive,
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(0).toInstant()));

        List<SlavePortfolio.Position> positionListVersionEight = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5", "5031.4",
            Date.from(OffsetDateTime.now().minusMonths(2).minusDays(2).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionEight = steps.createBaseMoney("46461.45",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 11);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 8,
            3, baseMoneyVersionEight, positionListVersionEight, Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()));

        List<SlavePortfolio.Position> positionSlaveVersionNine = threeSlavePositions111(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", "285.51",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "5", "105.29",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "8", "5031.4",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(1).toInstant()));
        SlavePortfolio.BaseMoneyPosition baseMoneyVersionNine = steps.createBaseMoney("31367.25",
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(2).toInstant()), (byte) 12);
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, 9,
            3, baseMoneyVersionNine, positionSlaveVersionNine,
            Date.from(OffsetDateTime.now().minusMonths(1).minusDays(1).toInstant()));

    }

    @Step("Рассчитываем стоимость портфеля: ")
    BigDecimal createPortfolioValueOnePeriod() {
        //Расчитываем стоимость порфеля на конец первого расчетного периода
        // формируем список позиций для запроса prices MD
        LocalDateTime lastDayFirstPeriod = LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).
            atStartOfDay();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

        String dateTs = fmt.format(lastDayFirstPeriod.atZone(UTC));

        String ListInst = instrument.instrumentSBER + "," + instrument.instrumentSU29009RMFS6;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(lastDayFirstPeriod);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(instrument.tickerSU29009RMFS6)
            .idKindQuery("ticker")
            .classCodeQuery(instrument.classCodeSU29009RMFS6)
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
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(quantitySBER).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantitySU29009RMFS6);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantitySU29009RMFS6);
                ;
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(new BigDecimal("53763.35"));
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }


    @Step("Рассчитываем стоимость портфеля: ")
    BigDecimal getPorfolioValue(String baseMoney, String quantity1, String quantity2, String quantity3, LocalDateTime cutDate) {
        // формируем список позиций для запроса prices MD
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutDate.atZone(UTC));
        String ListInst = instrument.instrumentSBER + "," + instrument.instrumentSU29009RMFS6 + "," + instrument.instrumentYNDX;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 3);
        // получаем данные для расчета по облигациям
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutDate);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(instrument.tickerSU29009RMFS6)
            .idKindQuery("ticker")
            .classCodeQuery(instrument.classCodeSU29009RMFS6)
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
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantity2);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantity2);
            }
            if (pair.getKey().equals(instrument.instrumentYNDX)) {
                valuePos3 = new BigDecimal(quantity3).multiply((BigDecimal) pair.getValue());
                price3 = (BigDecimal) pair.getValue();
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3).add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }


    @Step("Создаем запись в табл. result_fee: ")
    void createFeeResultFirst(OffsetDateTime updateTime, long subscriptionId) {
        Date startFirst = Date.from(updateTime.toLocalDate().atStartOfDay().toInstant(UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 4,
            startFirst, endFirst, context, new BigDecimal("65162.5"), endFirst);
    }

    @Step("Создаем записи в табл. result_fee: ")
    void createTwoPeriodFeeResult(OffsetDateTime startSubTime, long subscriptionId) {
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 4,
            startFirst, endFirst, context, new BigDecimal("65162.5"), endFirst);

        Date startSecond = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        Date endSecond = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmptySecond = new ArrayList<>();
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("310.79"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1069.65000"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("4942"))
            .priceTs(startFirst)
            .build());
        Context contextSec = Context.builder()
            .portfolioValue(new BigDecimal("79880.40000"))
            .positions(positionListEmptySecond)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 7,
            startSecond, endSecond, contextSec, new BigDecimal("79880.4"), endSecond);
    }

    @Step("Создаем портфлель slave с позициями: ")
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


    @Step("Добавляем позицию в портфлель slave: ")
    List<SlavePortfolio.Position> oneSlavePositions(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
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

    @Step("Добавляем позиции в портфлель slave: ")
    List<SlavePortfolio.Position> twoSlavePositionsNoBond(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal(quantityYNDX))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("4862.8"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }

    @Step("Ожидаем записи в табл.result_fee: ")
    void checkComparedToFeeVersion(int version, long subscriptionId) throws InterruptedException {
//        for (int i = 0; i < 5; i++) {
//            Thread.sleep(3000);
//            resultFee = resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, version);
//            if (resultFee.getVersion() != version) {
//                Thread.sleep(5000);
//            }
//        }
        await().atMost(Duration.ofSeconds(6)).pollDelay(Duration.ofSeconds(5)).until(() ->
            resultFeeDao.getResultFee(contractIdSlave, strategyId, subscriptionId, version), notNullValue());
    }

    @Step("Отправляем команду в TRACKING_CONTRACT_EVENT: ")
        //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = steps.createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaStringToByteSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }


    BigDecimal calculateNotBondPositionValue(LocalDateTime cut, BigDecimal qty, String ticker, String classCode) {
        // формируем список позиций для запроса prices MD
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.ms'Z'");
        String dateTs = fmt.format(cut.atZone(UTC));
        String ListInst = ticker + "_" + classCode;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 1);
        //выполняем расчеты стоимости позиции
        BigDecimal valuePos = pricesPos.values().stream().findFirst().get();
        BigDecimal positionValue = qty.multiply(valuePos);
        log.info("positionValue:  {}", positionValue);
        return positionValue;
    }

    @Step("Расситываем значение стоимость портфеля за рассчетный период c озицией типа bond: ")
    BigDecimal calculatePositionBondValue(LocalDateTime cutDate, BigDecimal qty, String ticker, String classCode) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutDate.atZone(UTC));
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutDate);
        String ListInst = ticker + "_" + classCode;
        //вызываем метод MD и сохраняем prices
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 1);
        //выполняем расчеты стоимости позиции Bond
        BigDecimal pricePosition = pricesPos.values().stream().findFirst().get();
        // получаем данные для расчета по облигациям
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(instrument.tickerSU29009RMFS6)
            .idKindQuery("ticker")
            .classCodeQuery(instrument.classCodeSU29009RMFS6)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        BigDecimal aciValue = new BigDecimal(resp.getBody().jsonPath().getString("[0].value"));
        BigDecimal nominal = new BigDecimal(resp.getBody().jsonPath().getString("[0].nominal"));
        //выполняем расчеты стоимости позиции Bond
        BigDecimal newPrice = pricePosition
            .multiply(nominal)
            .divide(BigDecimal.valueOf(100));
        BigDecimal newMinPriceIncrement = minPriceIncrement
            .multiply(nominal)
            .divide(BigDecimal.valueOf(100));
        BigDecimal roundNewPrice = newPrice
            .divide(minPriceIncrement, RoundingMode.HALF_DOWN)
            .multiply(minPriceIncrement);
        BigDecimal bondPrice = roundNewPrice.add(aciValue);
        BigDecimal valueBondPosition = bondPrice.multiply(qty);
        log.info("valuePortfolio:  {}", valueBondPosition);
        return valueBondPosition;
    }

    @Step("Cоздаем запись по заводу в slave_adjust: ")
    void createsSlaveAdjust(String contractId, UUID strategyId, OffsetDateTime createDate, long operationId,
                            OffsetDateTime changedAt, String currency, Boolean deleted, String quantity) {
        slaveAdjust = SlaveAdjust.builder()
            .contractId(contractId)
            .strategyId(strategyId)
            .createdAt(Date.from(createDate.toInstant()))
            .operationId(operationId)
            .quantity(new BigDecimal(quantity))
            .currency(currency)
            .deleted(deleted)
            .changedAt(Date.from(changedAt.toInstant()))
            .build();
        slaveAdjustDao.insertIntoSlaveAdjust(slaveAdjust);
    }

    @Step("Получаем список расчетных периодов: ")
    List<LocalDateTime> getDetermineSettlementPeriods(String contractId, UUID strategyId, long subId) {
        List<LocalDateTime> period = new ArrayList<>();
        LocalDateTime startedAt = null;
        LocalDateTime endAt = null;
        LocalDateTime nextDatePeriod = null;
        //получаем записи из табл result_fee
        List<ResultFee> resultFee = resultFeeDao.findListResultFee(contractId, strategyId, subId);
        //получаем запись о подписке
        subscription = subscriptionService.getSubscriptionByContract(contractId);
        //Определяем начало следующего расчетного периода startedAt: если нет записи в result_fee,
        // то берем дату старта подписки, иначе startedAt = следующая ближайшая "справа" (строго больше)
        // метка времени от settlement_period_started_at, соответствующая 1
        if (resultFee.size() == 0) {
            startedAt = subscription.getStartTime().toInstant().atZone(UTC).toLocalDate().atStartOfDay();
            period.add(startedAt);
        } else {
            startedAt = resultFee.get(0).getSettlementPeriodStartedAt().toInstant().atZone(UTC).toLocalDate()
                .plusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
            period.add(startedAt);
        }
        //Определяем метку времени окончания последнего расчетного периода endedAt:
//        если у найденной подписки subscription.end_time <> null, то считаем endedAt = найденный end_time,
//            иначе считаем endedAt = ближайшая "слева" (меньше или равна) метка времени от now(), соответствующее
        if (subscription.getEndTime() == null) {
            endAt = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();

        } else {
            endAt = subscription.getEndTime().toInstant().atZone(UTC).toLocalDate()
                .with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        }
        //разбиваем полученный интервал на периоды по 1 месяцу:
        nextDatePeriod = startedAt.with(TemporalAdjusters.firstDayOfNextMonth());
        period.add(nextDatePeriod);
        while (nextDatePeriod.compareTo(endAt) != 0) {
            nextDatePeriod = nextDatePeriod.with(TemporalAdjusters.firstDayOfNextMonth());
            period.add(nextDatePeriod);
        }
        return period;
    }

    @Step("Рассчитываем стоимость портфеля на расчетный период: ")
        //Расчитываем стоимость порфеля на конец первого расчетного периода
    BigDecimal getPortfolioValuePeriod(SlavePortfolio slavePortfolio, LocalDateTime lastDayFirstPeriod) {
        List<String> instrumentList = new ArrayList<>();
        String quantitySBER = "";
        String quantitySU29009RMFS6 = "";
        String quantityYNDX = "";
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            String ticker = slavePortfolio.getPositions().get(i).getTicker();
            String quantity = slavePortfolio.getPositions().get(i).getQuantity().toString();
            //в тестах используются 3 инструмента, выполняем расчет по этим инструментам
            //получаем количество по каждому инструменту и добавляем инструмент в список instrumentList для получения цен из MD
            if (ticker.equals(instrument.tickerSBER)) {
                instrumentList.add(instrument.instrumentSBER);
                quantitySBER = quantity;
            }
            if (ticker.equals(instrument.tickerSU29009RMFS6)) {
                instrumentList.add(instrument.instrumentSU29009RMFS6);
                quantitySU29009RMFS6 = quantity;
            }
            if (ticker.equals(instrument.tickerYNDX)) {
                instrumentList.add(instrument.instrumentYNDX);
                quantityYNDX = quantity;
            }
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(lastDayFirstPeriod.atZone(UTC));
        //преобразовываем полученные инструменты в строку для запроса prices MD
        if (instrumentList.size() != 0) {
            String listInst = getInstrumentString(instrumentList);
            //вызываем метод MD и сохраняем prices в Map
            Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(listInst, "last", dateTs, instrumentList.size());
            // получаем данные для расчета по облигациям
            DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String dateFireg = fmtFireg.format(lastDayFirstPeriod);
            List<String> getBondDate = steps.getDateBondFromInstrument(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
            String aciValue = getBondDate.get(0);
            String nominal = getBondDate.get(1);
            //выполняем расчеты стоимости портфеля умножаем price на количество позиций
            BigDecimal price1 = BigDecimal.ZERO;
            BigDecimal price2 = BigDecimal.ZERO;
            BigDecimal price3 = BigDecimal.ZERO;
            Iterator it = pricesPos.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                if (pair.getKey().equals(instrument.instrumentSBER)) {
                    valuePos1 = new BigDecimal(quantitySBER).multiply((BigDecimal) pair.getValue());
                    price1 = (BigDecimal) pair.getValue();
                }
                if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                    String priceTs = pair.getValue().toString();
                    valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantitySU29009RMFS6);
                    price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantitySU29009RMFS6);
                }
                if (pair.getKey().equals(instrument.instrumentYNDX)) {
                    valuePos3 = new BigDecimal(quantityYNDX).multiply((BigDecimal) pair.getValue());
                    price3 = (BigDecimal) pair.getValue();
                }
            }
        }
        //суммируем полученные значения и добавляем значение по базовой позиции
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3).add(slavePortfolio.getBaseMoneyPosition().getQuantity());
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }


    @Step("Преобразуем список инструментов в строку для запроса в MD: ")
    String getInstrumentString(List<String> instrumentList) {
        String listInst = "";
        for (int i = 0; i < instrumentList.size(); i++) {
            String instrument = instrumentList.get(i);
            listInst = listInst + instrument + ",";
        }
        listInst = listInst.substring(0, listInst.length() - 1);
        return listInst;
    }

    @Step("Расситываем значение HighWaterMark за рассчетный период: ")
    BigDecimal getHighWaterMarkPeriod(Date startedAt, Date cutDate, BigDecimal highWaterMarkFirstPeriod, BigDecimal portfolioValue) {
        //находим все заводы за период и оставляем только те записи, у которых slave_adjust.deleted = false;
        List<SlaveAdjust> slaveAdjustList = slaveAdjustDao.getSlaveAdjustByPeriod(contractIdSlave, strategyId, startedAt, cutDate)
            .stream().filter(s -> !s.getDeleted()).collect(Collectors.toList());
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal adjustValue = BigDecimal.ZERO;
        //проверяем, что если валюта завода рубли, то считаем сумму завода adjustValue = сумма (slave_adjust.quantity)
        for (int i = 0; i < slaveAdjustList.size(); i++) {
            if (slaveAdjustList.get(i).getCurrency().equals("rub")) {
                quantity = slaveAdjustList.get(i).getQuantity();
                //считаем сумму завода adjustValue
                adjustValue = adjustValue.add(quantity);
            } else {
                //если валюта завода usd или eur конвертируем сумму в рубли
                if (slaveAdjustList.get(i).getCurrency().equals("usd")) {
                    Date createAt = slaveAdjustList.get(i).getCreatedAt();
                    DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String dateTs = utcFormat.format(createAt);
                    //запрашиваем цену по usd в MD на момент завода
                    BigDecimal price = new BigDecimal(steps.getPriceFromMarketData(instrument.instrumentUSD, "last", dateTs));
                    //конвертируем сумму в рубли по формуле: slave_adjust.quantity обрабатываемого завода * price
                    quantity = slaveAdjustList.get(i).getQuantity().multiply(price);
                    //считаем сумму завода adjustValue
                    adjustValue = adjustValue.add(quantity);
                }
                if (slaveAdjustList.get(i).getCurrency().equals("eur")) {
                    Date createAt = slaveAdjustList.get(i).getCreatedAt();
                    DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String dateTs = utcFormat.format(createAt);
                    //запрашиваем цену по eur в MD на момент завода
                    BigDecimal price = new BigDecimal(steps.getPriceFromMarketData(instrument.instrumentEURRUBTOM, "last", dateTs));
                    //конвертируем сумму в рубли по формуле: slave_adjust.quantity обрабатываемого завода * price
                    quantity = slaveAdjustList.get(i).getQuantity().multiply(price);
                    //считаем сумму завода adjustValue
                    adjustValue = adjustValue.add(quantity);
                }
            }
        }
        //определяем highWaterMark, на основе которого необходимо рассчитать комиссию = найденный result_fee.high_water_mark
        // (последняя отметка high water mark) + сумма всех заводов adjustValue
        BigDecimal highWaterMark = highWaterMarkFirstPeriod.add(adjustValue);
        //определяем новый newHighWaterMark - выбираем максимальное из:рассчитанный highWaterMark, рассчитанный portfolioValue.
        BigDecimal newHighWaterMark = max(portfolioValue, highWaterMark);
        log.info("newHighWaterMark:  {}", newHighWaterMark);
        return newHighWaterMark;
    }

}
