package stpTrackingAnalytics.calculateSignalFrequency;

import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.SignalFrequency;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;


import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_ANALYTICS_COMMAND;

@Slf4j
@Epic("calculateSignalFrequency Пересчет частоты создания сигналов")
@Feature("TAP-9596")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@Tags({@Tag("stp-tracking-analytics"), @Tag("calculateSignalFrequency")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAnalyticsStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingSiebelConfiguration.class
})

public class СalculateSignalFrequencyTest {
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    SignalsCountDao signalsCountDao;
    @Autowired
    SignalFrequencyDao signalFrequencyDao;
    @Autowired
    StpTrackingAnalyticsSteps steps;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    StrategyTailValueDao strategyTailValueDao;


    UUID strategyId;
    SignalFrequency signalFrequency;
    String contractIdMaster;
    Client clientSlave;
    String contractIdSlave;
    UUID investIdSlave;
    UUID investIdMaster;


    @BeforeAll
    void getDataClients() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов

        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdMasterAnalytics);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlaveOne = steps.getBrokerAccounts(siebel.siebelIdAnalyticsSlaveOne);
        investIdSlave = resAccountSlaveOne.getInvestId();
        contractIdSlave = resAccountSlaveOne.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(contractIdSlave, investIdSlave);
        steps.deleteDataFromDb(contractIdMaster, investIdMaster);
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                signalFrequencyDao.deleteSignalFrequencyByStrategyId(strategyId);
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
                trackingService.deleteStrategy(steps.strategy);
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
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                strategyTailValueDao.deleteStrategyTailValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> cutTimeCalculate() {
        return Stream.of(
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now(), StrategyStatus.active),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3), StrategyStatus.frozen)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTimeCalculate")
    @AllureId("830184")
    @DisplayName("C830184.CalculateSignalFrequency.Пересчет частоты создания сигналов, если operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C830184(OffsetDateTime createTime, OffsetDateTime cutTime, StrategyStatus status) {

        log.info("strategyId:  {}", strategyId);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, "AutoTest", "AutoTest", StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNAL_FREQUENCY,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Date start = Date.from(cutTime.minusDays(30).toInstant());
        Date end = Date.from(cutTime.toInstant());
        int count = countUniqueMasterSignalDays(strategyId, start, end);
        checkMasterSignalFrequency(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalFrequency.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("количество сигналов по стратегии не равно", signalFrequency.getCount(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTimeCalculate")
    @AllureId("832253")
    @DisplayName("C832253.CalculateSignalFrequency.Пересчет частоты создания сигналов, если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C832253(OffsetDateTime createTime, OffsetDateTime cutTime, StrategyStatus status) {

        log.info("strategyId:  {}", strategyId);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, "AutoTest", "AutoTest", StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNAL_FREQUENCY,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        checkMasterSignalFrequency(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalFrequency.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date start = Date.from(cutTime.minusDays(30).toInstant());
        Date end = Date.from(cutTime.toInstant());
        int count = countUniqueMasterSignalDays(strategyId, start, end);
        assertThat("частота создания сигналов по стратегии не равно", signalFrequency.getCount(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @Test
    @AllureId("835569")
    @DisplayName("C835569.CalculateSignalFrequency.Найдена запись в таб. signal_frequency по ключу: strategy_id , cut, если operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C835569() {
        log.info("strategyId:  {}", strategyId);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, "AutoTest", "AutoTest", StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalRepeat(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNAL_FREQUENCY,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        checkMasterSignalFrequency(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalFrequency.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date start = Date.from(cutTime.minusDays(30).toInstant());
        Date end = Date.from(cutTime.toInstant());
        int count = countUniqueMasterSignalDays(strategyId, start, end);
        assertThat("частота создания сигналов по стратегии не равно", signalFrequency.getCount(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        createMasterSignal(29, 2, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        //отправляем событие в топик kafka tracking.analytics.command повторно
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
//        Thread.sleep(5000);
        long countRecord = signalFrequencyDao.count(strategyId);
        assertThat("время cut не равно", countRecord, is(1L));
        signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId);
        assertThat("частота создания сигналов по стратегии не равно", signalFrequency.getCount(), is(count));
    }


    @SneakyThrows
    @Test
    @AllureId("835781")
    @DisplayName("C835781.CalculateSignalFrequency.Найдена запись в таб. signal_frequency по ключу: strategy_id , cut," +
        " если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C835781() {
        log.info("strategyId:  {}", strategyId);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, "AutoTest", "AutoTest", StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.frozen, 0, LocalDateTime.now(), null);
        //создаем записи по сигналу на разные позиции
        createMasterSignal(31, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        ByteString strategyIdByte = byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNAL_FREQUENCY,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        checkMasterSignalFrequency(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalFrequency.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date start = Date.from(cutTime.minusDays(30).toInstant());
        Date end = Date.from(cutTime.toInstant());
        int count = countUniqueMasterSignalDays(strategyId, start, end);
        assertThat("частота создания сигналов не равно", signalFrequency.getCount(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем еще сигналы
        createMasterSignal(1, 1, 9, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "2", 12);
        createMasterSignal(0, 2, 10, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
        //отправляем событие в топик kafka tracking.analytics.command повторно
        OffsetDateTime createTimeNew = OffsetDateTime.now();
        Tracking.AnalyticsCommand reCalculateCommand = steps.createCommandAnalytics(createTimeNew, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNAL_FREQUENCY,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", reCalculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytesNew = reCalculateCommand.toByteArray();
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytesNew);
        long countRecord = signalFrequencyDao.count(strategyId);
        assertThat("время cut не равно", countRecord, is(1L));
        checkMasterSignalFrequency(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId), notNullValue());
        LocalDateTime cutNew = LocalDateTime.ofInstant(signalFrequency.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommandNew = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        int countNew = countUniqueMasterSignalDays(strategyId, start, end);
        assertThat("частота создания сигналов по стратегии не равно", signalFrequency.getCount(), is(countNew));
        assertThat("время cut не равно", true, is(cutNew.equals(cutInCommandNew)));
    }


    // методы для работы тестов*************************************************************************
    @Step("Переводим значение stratedyId в byte: ")
    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }

    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }

    @Step("Создаем запись по сигналам мастера в табл. master_signal: ")
    void createMasterSignal(int minusDays, int minusHours, int version, UUID strategyId, String ticker, String tradingClearingAccount,
                            String price, String quantity, int action) {
        LocalDateTime time = LocalDateTime.now().minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyId)
            .version(version)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .action((byte) action)
            .state((byte) 1)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .createdAt(convertedDatetime)
            .build();
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }


    void createTestDateToMasterSignal(UUID strategyId) {
        createMasterSignal(31, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 9, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 10, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
    }


    void createTestDateToMasterSignalRepeat(UUID strategyId) {
        createMasterSignal(31, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(3, 1, 7, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 9, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 10, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
    }

    //считаем количество уникальных дней
    @Step("Считаем количество уникальных дней: ")
    public Integer countUniqueMasterSignalDays(UUID strategyId, Date start, Date end) {
        return new HashSet<>(masterSignalDao.getUniqMasterSignalDaysByPeriod(strategyId, start, end)).size();
    }


    // ожидаем версию портфеля slave
    void checkMasterSignalFrequency(UUID strategyId) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3000);
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId);
            if (signalFrequency.getStrategyId() == null) {
                Thread.sleep(5000);
            }
        }
    }

}
