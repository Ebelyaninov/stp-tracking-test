package stpTrackingAnalytics.calculateMasterPortfolioTopPositions;


import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
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
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
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
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_ANALYTICS_COMMAND;

@Slf4j
@Epic("calculateMasterPortfolioTopPositions Пересчет топа-позиций master-портфеля")
@Feature("TAP-9318")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAnalyticsStepsConfiguration.class
})
public class CalculateMasterPortfolioTopPositionsTest {

    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    MasterPortfolioTopPositionsDao masterPortfolioTopPositionsDao;
    @Autowired
    StpTrackingAnalyticsSteps steps;

    UUID strategyId;
    MasterPortfolioTopPositions masterPortfolioTopPositions;


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
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

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTimeCalculate")
    @AllureId("953112")
    @DisplayName("C953112.CalculateMasterPortfolioTopPositions.Пересчет топа-позиций master-портфеля за период")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C953112(OffsetDateTime createTime, OffsetDateTime cutTime, Tracking.AnalyticsCommand.Operation operation) {
        strategyId = UUID.randomUUID();
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
        await().atMost(TEN_SECONDS).until(() ->
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
        assertThat("списки по позициям не равны", true, is(masterPortfolioTopPositions.getPositions().equals(expectedList)));
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
        await().atMost(TEN_SECONDS).until(() ->
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
        assertThat("списки по позициям не равны", true, is(masterPortfolioTopPositions.getPositions().equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @Test
    @AllureId("953119")
    @DisplayName("C953119.CalculateMasterPortfolioTopPositions.Найдена запись в таблице master_portfolio_top_positionsпо ключу: strategy_id, cut," +
        " если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C953119() {
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(3);
        strategyId = UUID.randomUUID();
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
//        Thread.sleep(5000);
        //получаем из табл. master_portfolio_top_positions рассчитанные топовые позиции
        await().atMost(TEN_SECONDS).until(() ->
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
        assertThat("списки по позициям не равны", true, is(masterPortfolioTopPositions.getPositions().equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем данные
        createMasterSignal(30, 2, 6, strategyId, "ABBV", "L01+00000SPB",
            "90.18", "6", 11);
        createMasterSignal(29, 2, 7, strategyId, "NOK", "L01+00000SPB",
            "3.98", "7", 12);
        createMasterSignal(5, 4, 8, strategyId, "AAPL", "L01+00000SPB",
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
        assertThat("списки по позициям не равны", true, is(masterPortfolioTopPositions.getPositions().equals(expectedListNew)));
    }


    @SneakyThrows
    @Test
    @AllureId("966109")
    @DisplayName("C966109.CalculateMasterPortfolioTopPositions.Найдена запись в таблице master_portfolio_top_positionsпо ключу: strategy_id, cut," +
        " если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C966109() {
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(3);
        strategyId = UUID.randomUUID();
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
        await().atMost(TEN_SECONDS).until(() ->
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
        assertThat("списки по позициям не равны", true, is(masterPortfolioTopPositions.getPositions().equals(expectedList)));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем данные
        createMasterSignal(30, 2, 6, strategyId, "ABBV", "L01+00000SPB",
            "90.18", "6", 11);
        createMasterSignal(29, 2, 7, strategyId, "NOK", "L01+00000SPB",
            "3.98", "7", 12);
        createMasterSignal(5, 4, 8, strategyId, "AAPL", "L01+00000SPB",
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
        assertThat("списки по позициям не равны", true, is(masterPortfolioTopPositions.getPositions().equals(expectedList)));
    }


    // методы для работы тестов*************************************************************************

    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }

    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }


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
        createMasterSignal(91, 1, 2, strategyId, "NOK", "NDS000000001",
            "4.06", "2", 12);
        createMasterSignal(90, 0, 3, strategyId, "ABBV", "NDS000000001",
            "90.18", "1", 12);
        createMasterSignal(89, 7, 4, strategyId, "NOK", "NDS000000001",
            "4.06", "1", 11);
        createMasterSignal(31, 1, 5, strategyId, "NOK", "NDS000000001",
            "4.07", "4", 12);
        createMasterSignal(30, 2, 6, strategyId, "ABBV", "NDS000000001",
            "90.18", "6", 11);
        createMasterSignal(29, 2, 7, strategyId, "NOK", "NDS000000001",
            "3.98", "7", 12);
        createMasterSignal(5, 4, 8, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(4, 2, 9, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(3, 1, 10, strategyId, "ABBV", "NDS000000001",
            "90.18", "3", 11);
        createMasterSignal(2, 1, 11, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(1, 4, 12, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(0, 2, 13, strategyId, "NOK", "NDS000000001",
            "3.17", "4", 12);
        createMasterSignal(0, 1, 14, strategyId, "NOK", "NDS000000001",
            "3.09", "4", 12);
    }


    void createTestDateToMasterSignalRepeat(UUID strategyId) {
        createMasterSignal(91, 1, 2, strategyId, "NOK", "NDS000000001",
            "4.06", "2", 12);
        createMasterSignal(90, 0, 3, strategyId, "ABBV", "NDS000000001",
            "90.18", "1", 12);
        createMasterSignal(89, 7, 4, strategyId, "NOK", "NDS000000001",
            "4.06", "1", 11);
        createMasterSignal(31, 1, 5, strategyId, "NOK", "NDS000000001",
            "4.07", "4", 12);
        createMasterSignal(4, 2, 9, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(3, 1, 10, strategyId, "ABBV", "NDS000000001",
            "90.18", "3", 11);
        createMasterSignal(2, 1, 11, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(1, 4, 12, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(0, 2, 13, strategyId, "NOK", "NDS000000001",
            "3.17", "4", 12);
        createMasterSignal(0, 1, 14, strategyId, "NOK", "NDS000000001",
            "3.09", "4", 12);
    }


    void createTestDateToMasterSignalNotPeriodTopPos(UUID strategyId) {
        createMasterSignal(94, 1, 2, strategyId, "NOK", "NDS000000001",
            "4.06", "2", 12);
        createMasterSignal(93, 3, 3, strategyId, "ABBV", "NDS000000001",
            "90.18", "1", 12);
        createMasterSignal(2, 1, 4, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(1, 4, 5, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(0, 2, 6, strategyId, "NOK", "L01+00000SPB",
            "3.17", "4", 12);
        createMasterSignal(0, 1, 7, strategyId, "NOK", "L01+00000SPB",
            "3.09", "4", 12);
    }


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


}
