package stpTrackingAnalytics.calculateMasterPortfolioTopPositions;


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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.PositionId;
import ru.qa.tinkoff.investTracking.entities.TopPosition;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioTopPositionsDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_ANALYTICS_COMMAND;

@Slf4j
@Epic("calculateMasterPortfolioTopPositions Пересчет топа-позиций master-портфеля")
@Feature("TAP-9318")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@Tags({@Tag("stp-tracking-analytics"), @Tag("calculateMasterPortfolioTopPositions")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAnalyticsStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})
public class CalculateMasterPortfolioTopPositionsTest {

    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    MasterPortfolioTopPositionsDao masterPortfolioTopPositionsDao;
    @Autowired
    StpTrackingAnalyticsSteps steps;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;

    UUID strategyId;
    MasterPortfolioTopPositions masterPortfolioTopPositions;

    String contractIdMaster;
    UUID investIdMaster;

    @BeforeAll
    void getDataClients() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdMasterAnalytics1);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(contractIdMaster, investIdMaster);
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(steps.strategy);
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
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioTopPositionsDao.deleteMasterPortfolioTopPositionsByStrategyId(strategyId);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> cutTimeCalculate() {
        return Stream.of(
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now(), Tracking.AnalyticsCommand.Operation.CALCULATE),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3), Tracking.AnalyticsCommand.Operation.CALCULATE),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now(), Tracking.AnalyticsCommand.Operation.RECALCULATE),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3), Tracking.AnalyticsCommand.Operation.RECALCULATE)
        );
    }


    private static Stream<Arguments> provideStrategyStatus() {
        return Stream.of(
            Arguments.of(StrategyStatus.active, LocalDateTime.now()),
            Arguments.of(StrategyStatus.frozen, LocalDateTime.now())

        );
    }


    private static Stream<Arguments> provideNotStrategyStatus() {
        return Stream.of(
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE, StrategyStatus.draft, null, null),
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE, StrategyStatus.closed, LocalDateTime.now().minusDays(1), LocalDateTime.now())

        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTimeCalculate")
    @AllureId("953112")
    @DisplayName("C953112.CalculateMasterPortfolioTopPositions.Пересчет топа-позиций master-портфеля за период")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C953112(OffsetDateTime createTime, OffsetDateTime cutTime, Tracking.AnalyticsCommand.Operation operation) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), "Top", StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand command = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS, strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем из табл. master_portfolio_top_positions рассчитанные топовые позиции
        await().pollDelay(Duration.ofSeconds(1));
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            masterPortfolioTopPositions = masterPortfolioTopPositionsDao
                .getMasterPortfolioTopPositions(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioTopPositions.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        // Отбираем сигналы из created_at_master_signal за заданный период
        Date start = Date.from(cutTime.minusDays(90).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<PositionId> positionIdList = masterSignalDao.getMasterSignalPositionIdsByPeriod(strategyId, start, end);
        //группируем записи по ticker + trading_clearing_account, и считаем count
        Map<PositionId, Long> positionIdLongMap = positionIdList.stream()
            .collect(groupingBy(Function.identity(), counting()));
        //Сортируем полученные позиции и выбираем первые 3 из них
        List<TopPosition> expectedList = getExpectedListPosition(positionIdLongMap);
        //сверяем с данными в master_portfolio_top_positions
        List<TopPosition> actual = masterPortfolioTopPositions.getPositions().stream()
            .map(position -> TopPosition.builder()
                .ticker(position.getTicker())
                .tradingClearingAccount(position.getTradingClearingAccount())
                .signalsCount(position.getSignalsCount())
                .build())
            .collect(Collectors.toList());
        assertThat("списки по позициям не равны", true, is(actual.equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    private static Stream<Arguments> cutTimeNotPosition() {
        return Stream.of(
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3), Tracking.AnalyticsCommand.Operation.CALCULATE),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3), Tracking.AnalyticsCommand.Operation.RECALCULATE)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTimeNotPosition")
    @AllureId("962639")
    @DisplayName("C962639.CalculateMasterPortfolioTopPositions.Пересчет топа-позиций master-портфеля за период")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C962639(OffsetDateTime createTime, OffsetDateTime cutTime, Tracking.AnalyticsCommand.Operation operation) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), "Top", StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalNotPeriodTopPos(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand command = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS, strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем из табл. master_portfolio_top_positions рассчитанные топовые позиции
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            masterPortfolioTopPositions = masterPortfolioTopPositionsDao
                .getMasterPortfolioTopPositions(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioTopPositions.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        // Отбираем сигналы из created_at_master_signal за заданный период
        Date start = Date.from(cutTime.minusDays(90).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<PositionId> positionIdList = masterSignalDao.getMasterSignalPositionIdsByPeriod(strategyId, start, end);
        //группируем записи по ticker + trading_clearing_account, и считаем count
        Map<PositionId, Long> positionIdLongMap = positionIdList.stream()
            .collect(groupingBy(Function.identity(), counting()));
        //Сортируем полученные позиции и выбираем первые 3 из них
        List<TopPosition> expectedList = getExpectedListPosition(positionIdLongMap);
        //сверяем с данными в master_portfolio_top_positions
        List<TopPosition> actual = masterPortfolioTopPositions.getPositions().stream()
            .map(position -> TopPosition.builder()
                .ticker(position.getTicker())
                .tradingClearingAccount(position.getTradingClearingAccount())
                .signalsCount(position.getSignalsCount())
                .build())
            .collect(Collectors.toList());
        assertThat("списки по позициям не равны", true, is(actual.equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @MethodSource("provideStrategyStatus")
    @ParameterizedTest
    @AllureId("953119")
    @DisplayName("C953119.CalculateMasterPortfolioTopPositions.Найдена запись в таблице master_portfolio_top_positionsпо ключу: strategy_id, cut," +
        " если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C953119(StrategyStatus status, LocalDateTime time) {
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(3);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), "Top", StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, time, null);
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalRepeat(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand command = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем из табл. master_portfolio_top_positions рассчитанные топовые позиции
        await().pollDelay(Duration.ofMillis(500));
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            masterPortfolioTopPositions = masterPortfolioTopPositionsDao
                .getMasterPortfolioTopPositions(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioTopPositions.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        // Отбираем сигналы из created_at_master_signal за заданный период
        Date start = Date.from(cutTime.minusDays(90).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<PositionId> positionIdList = masterSignalDao.getMasterSignalPositionIdsByPeriod(strategyId, start, end);
        //группируем записи по ticker + trading_clearing_account, и считаем count
        Map<PositionId, Long> positionIdLongMap = positionIdList.stream()
            .collect(groupingBy(Function.identity(), counting()));
        //Сортируем полученные позиции и выбираем первые 3 из них
        List<TopPosition> expectedList = getExpectedListPosition(positionIdLongMap);
        //сверяем с данными в master_portfolio_top_positions
        List<TopPosition> actual = masterPortfolioTopPositions.getPositions().stream()
            .map(position -> TopPosition.builder()
                .ticker(position.getTicker())
                .tradingClearingAccount(position.getTradingClearingAccount())
                .signalsCount(position.getSignalsCount())
                .build())
            .collect(Collectors.toList());
        assertThat("списки по позициям не равны", true, is(actual.equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем данные
        createMasterSignal(30, 2, 6, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 7, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 8, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand commandNew = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS,
            strategyIdByte);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytesNew = commandNew.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command повторно
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytesNew);
        long countRecord = masterPortfolioTopPositionsDao.count(strategyId);
        assertThat("время cut не равно", countRecord, is(1L));
        List<PositionId> positionIdListNew = masterSignalDao.getMasterSignalPositionIdsByPeriod(strategyId, start, end);
        //группируем записи по ticker + trading_clearing_account, и считаем count
        Map<PositionId, Long> positionIdLongMapNew = positionIdListNew.stream()
            .collect(groupingBy(Function.identity(), counting()));
        //Сортируем полученные позиции и выбираем первые 3 из них
        List<TopPosition> expectedListNew = getExpectedListPosition(positionIdLongMapNew);
        masterPortfolioTopPositions = masterPortfolioTopPositionsDao
            .getMasterPortfolioTopPositions(strategyId);
        //сверяем с данными в master_portfolio_top_positions
        List<TopPosition> actualNew = masterPortfolioTopPositions.getPositions().stream()
            .map(position -> TopPosition.builder()
                .ticker(position.getTicker())
                .tradingClearingAccount(position.getTradingClearingAccount())
                .signalsCount(position.getSignalsCount())
                .build())
            .collect(Collectors.toList());
        assertThat("списки по позициям не равны", true, is(actualNew.equals(expectedListNew)));
    }


    @SneakyThrows
    @MethodSource("provideStrategyStatus")
    @ParameterizedTest
    @AllureId("966109")
    @DisplayName("C966109.CalculateMasterPortfolioTopPositions.Найдена запись в таблице master_portfolio_top_positionsпо ключу: strategy_id, cut," +
        " если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C966109(StrategyStatus status, LocalDateTime time) {
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(3);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), "Top", StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, time, null);
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalRepeat(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand command = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем из табл. master_portfolio_top_positions рассчитанные топовые позиции
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            masterPortfolioTopPositions = masterPortfolioTopPositionsDao
                .getMasterPortfolioTopPositions(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioTopPositions.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        // Отбираем сигналы из created_at_master_signal за заданный период
        Date start = Date.from(cutTime.minusDays(90).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<PositionId> positionIdList = masterSignalDao.getMasterSignalPositionIdsByPeriod(strategyId, start, end);
        //группируем записи по ticker + trading_clearing_account, и считаем count
        Map<PositionId, Long> positionIdLongMap = positionIdList.stream()
            .collect(groupingBy(Function.identity(), counting()));
        //Сортируем полученные позиции и выбираем первые 3 из них
        List<TopPosition> expectedList = getExpectedListPosition(positionIdLongMap);
        //сверяем с данными в master_portfolio_top_positions
        List<TopPosition> actual = masterPortfolioTopPositions.getPositions().stream()
            .map(position -> TopPosition.builder()
                .ticker(position.getTicker())
                .tradingClearingAccount(position.getTradingClearingAccount())
                .signalsCount(position.getSignalsCount())
                .build())
            .collect(Collectors.toList());
        assertThat("списки по позициям не равны", true, is(actual.equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем данные
        createMasterSignal(30, 2, 6, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 7, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 8, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand commandNew = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS,
            strategyIdByte);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytesNew = commandNew.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command повторно
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytesNew);
        long countRecord = masterPortfolioTopPositionsDao.count(strategyId);
        assertThat("время cut не равно", countRecord, is(1L));
        masterPortfolioTopPositions = masterPortfolioTopPositionsDao
            .getMasterPortfolioTopPositions(strategyId);
        //сверяем с данными в master_portfolio_top_positions
        List<TopPosition> actualNew = masterPortfolioTopPositions.getPositions().stream()
            .map(position -> TopPosition.builder()
                .ticker(position.getTicker())
                .tradingClearingAccount(position.getTradingClearingAccount())
                .signalsCount(position.getSignalsCount())
                .build())
            .collect(Collectors.toList());
        assertThat("списки по позициям не равны", true, is(actualNew.equals(expectedList)));
    }


    @SneakyThrows
    @MethodSource("provideNotStrategyStatus")
    @ParameterizedTest
    @AllureId("1886683")
    @DisplayName("1886683 CalculateMasterPortfolioTopPositions. Strategy.status NOT IN (active, frozen)")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C1886683(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status, LocalDateTime activationTime, LocalDateTime closedTime) {
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(3);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), "Top", StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, activationTime, closedTime);
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalRepeat(strategyId);
        ByteString strategyIdByte = byteString(strategyId);
        //создаем команду для пересчета топа-позиций master-портфеля
        Tracking.AnalyticsCommand command = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_TOP_POSITIONS,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем из табл. master_portfolio_top_positions рассчитанные топовые позиции
        await().pollDelay(Duration.ofMillis(500));
        List<MasterPortfolioTopPositions> masterPortfolioTopPositions = masterPortfolioTopPositionsDao.getMasterPortfolioTopPositionsList(strategyId);
        // проверяем что ни одной записи не найдено
        assertThat("найдена запись", masterPortfolioTopPositions.size(), is(0));

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

    @Step("Создаем записи по сигналам мастера в табл. master_signal: ")
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
        createMasterSignal(91, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.06", "2", 12);
        createMasterSignal(90, 0, 3, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "1", 12);
        createMasterSignal(89, 7, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.06", "1", 11);
        createMasterSignal(31, 1, 5, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 6, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 7, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 8, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 9, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 10, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 11, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 12, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 13, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 14, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
    }


    void createTestDateToMasterSignalRepeat(UUID strategyId) {
        createMasterSignal(91, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.06", "2", 12);
        createMasterSignal(90, 0, 3, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "1", 12);
        createMasterSignal(89, 7, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.06", "1", 11);
        createMasterSignal(31, 1, 5, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.07", "4", 12);
        createMasterSignal(4, 2, 9, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 10, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 11, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 12, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 13, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 14, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
    }


    void createTestDateToMasterSignalNotPeriodTopPos(UUID strategyId) {
        createMasterSignal(94, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.06", "2", 12);
        createMasterSignal(93, 3, 3, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "90.18", "1", 12);
        createMasterSignal(2, 1, 4, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 5, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 6, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 7, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
    }


    @Step("Сортируем полученные позиции и выбираем первые 3 из них")
    List<TopPosition> getExpectedListPosition(Map<PositionId, Long> positionIdLongMap) {
        List<TopPosition> expectedList = positionIdLongMap.entrySet().stream()
            .sorted((o1, o2) -> {
                int c = Long.compare(o1.getValue(), o2.getValue());
                if (c != 0) {
                    // по убыванию, поэтому "-"
                    return -c;
                }
                c = o1.getKey().getTicker().compareTo(o2.getKey().getTicker());
                if (c != 0) {
                    return c;
                }
                return o1.getKey().getTradingClearingAccount().compareTo(o2.getKey().getTradingClearingAccount());
            })
            .limit(3)
            .map(entry -> {
                return TopPosition.builder()
                    .ticker(entry.getKey().getTicker())
                    .tradingClearingAccount(entry.getKey().getTradingClearingAccount())
                    .signalsCount(Math.toIntExact(entry.getValue()))
                    .build();
            })
            .collect(Collectors.toList());
        return expectedList;
    }


//    // ожидаем версию портфеля slave
//    void checkMasterPortfolioTopPositions(UUID strategyId) throws InterruptedException {
//        for (int i = 0; i < 5; i++) {
//            Thread.sleep(3000);
//            masterPortfolioTopPositions = masterPortfolioTopPositionsDao.getMasterPortfolioTopPositions(strategyId);
//            if (masterPortfolioTopPositions.getStrategyId() == null) {
//                Thread.sleep(5000);
//            }
//        }
//    }


}
