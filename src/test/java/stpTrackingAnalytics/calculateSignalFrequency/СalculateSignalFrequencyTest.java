package stpTrackingAnalytics.calculateSignalFrequency;

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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.SignalFrequency;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.SignalFrequencyDao;
import ru.qa.tinkoff.investTracking.services.SignalsCountDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAnalyticsStepsConfiguration.class
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
    UUID strategyId;
    SignalFrequency signalFrequency;


    final String tickerNok = "NOK";
    final String tradingClearingAccountNok = "TKCBM_TCAB";

    final String tickerGazprom = "XS0191754729";
    final String tradingClearingAccountGazprom = "L01+00002F00";

    final String tickerAbbV = "ABBV";
    final String tradingClearingAccountAbbV = "TKCBM_TCAB";

    final String tickerApple = "AAPL";
    final String tradingClearingAccountApple = "TKCBM_TCAB";


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
        });
    }

    private static Stream<Arguments> cutTimeCalculate() {
        return Stream.of(
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now()),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3))
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTimeCalculate")
    @AllureId("830184")
    @DisplayName("C830184.CalculateSignalFrequency.Пересчет частоты создания сигналов, если operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C830184(OffsetDateTime createTime, OffsetDateTime cutTime) {
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
//        Thread.sleep(5000);
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
    void C832253(OffsetDateTime createTime, OffsetDateTime cutTime) {
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
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
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
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
        createMasterSignal(29, 2, 4, strategyId, tickerNok, tradingClearingAccountNok,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, tickerApple, tradingClearingAccountApple,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, tickerApple, tradingClearingAccountApple,
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
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createMasterSignal(31, 1, 2, strategyId, tickerNok, tradingClearingAccountNok,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, tickerAbbV, tradingClearingAccountAbbV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, tickerNok, tradingClearingAccountNok,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, tickerApple, tradingClearingAccountApple,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, tickerApple, tradingClearingAccountApple,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, tickerAbbV, tradingClearingAccountApple,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, tickerGazprom, tradingClearingAccountGazprom,
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
        createMasterSignal(1, 1, 9, strategyId, tickerAbbV, tradingClearingAccountAbbV,
            "90.18", "2", 12);
        createMasterSignal(0, 2, 10, strategyId, tickerNok, tradingClearingAccountNok,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, tickerNok, tradingClearingAccountNok,
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
        createMasterSignal(31, 1, 2, strategyId, tickerNok, tradingClearingAccountNok,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, tickerAbbV, tradingClearingAccountAbbV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, tickerNok, tradingClearingAccountNok,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, tickerApple, tradingClearingAccountApple,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, tickerApple, tradingClearingAccountApple,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, tickerAbbV, tradingClearingAccountAbbV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, tickerGazprom, tradingClearingAccountGazprom,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 9, strategyId, tickerGazprom, tradingClearingAccountGazprom,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 10, strategyId, tickerNok, tradingClearingAccountNok,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, tickerNok, tradingClearingAccountNok,
            "3.09", "4", 12);
    }


    void createTestDateToMasterSignalRepeat(UUID strategyId) {
        createMasterSignal(31, 1, 2, strategyId, tickerNok, tradingClearingAccountNok,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, tickerAbbV, tradingClearingAccountAbbV,
            "90.18", "6", 11);
        createMasterSignal(3, 1, 7, strategyId, tickerAbbV, tradingClearingAccountAbbV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, tickerGazprom, tradingClearingAccountGazprom,
            "190.18", "1", 12);
        createMasterSignal(1, 4, 9, strategyId, tickerGazprom, tradingClearingAccountGazprom,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 10, strategyId, tickerNok, tradingClearingAccountNok,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, tickerNok, tradingClearingAccountNok,
            "3.09", "4", 12);
    }

    //считаем количество уникальных дней
    public Integer countUniqueMasterSignalDays(UUID strategyId, Date start, Date end) {
        return new HashSet<>(masterSignalDao.getUniqMasterSignalDaysByPeriod(strategyId, start, end)).size();
    }


    // ожидаем версию портфеля slave
    void checkMasterSignalFrequency(UUID strategyId) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            signalFrequency = signalFrequencyDao.getSignalFrequencyByStrategyId(strategyId);
            if (signalFrequency.getStrategyId() == null) {
                Thread.sleep(5000);
            }
        }
    }

}
