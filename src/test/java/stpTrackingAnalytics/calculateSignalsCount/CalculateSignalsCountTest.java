package stpTrackingAnalytics.calculateSignalsCount;


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
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.SignalsCount;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.SignalsCountDao;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;

import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.tinkoff.trading.tracking.Tracking;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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
@Epic("calculateSignalsCount - Расчет количества сигналов, созданных в рамках стратегии")
@Feature("TAP-9595")
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
public class CalculateSignalsCountTest {
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    SignalsCountDao signalsCountDao;
    @Autowired
    StpTrackingAnalyticsSteps steps;
    SignalsCount signalsCount;
    UUID strategyId;

    private static Stream<Arguments> cutTime() {
        return Stream.of(
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now()),
            Arguments.of(OffsetDateTime.now(), OffsetDateTime.now().minusDays(3))
        );
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                signalsCountDao.deleteSignalsCountByStratedyId(strategyId);
            } catch (Exception e) {
            }
        });
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTime")
    @AllureId("829212")
    @DisplayName("C829212.CalculateSignalsCount.Расчет количества сигналов, созданных в рамках стратегии, если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C829212(OffsetDateTime createTime, OffsetDateTime cutTime) {
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        Thread.sleep(5000);
        ByteString strategyIdByte = byteString(strategyId);
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNALS_COUNT,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        await().atMost(TEN_SECONDS).until(() ->
            signalsCount = signalsCountDao.getSignalsCountByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalsCount.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date createAt = Date.from(cutTime.toInstant());
        int count = masterSignalDao.countCreatedAtMasterSignal(strategyId, createAt);
        assertThat("количество сигналов по стратегии не равно", signalsCount.getValue(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("cutTime")
    @AllureId("827094")
    @DisplayName("C827094.CalculateSignalsCount.Расчет количества сигналов, созданных в рамках стратегии, если operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C827094(OffsetDateTime createTime, OffsetDateTime cutTime) {
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        Thread.sleep(5000);
        ByteString strategyIdByte = byteString(strategyId);
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNALS_COUNT,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        await().atMost(TEN_SECONDS).until(() ->
            signalsCount = signalsCountDao.getSignalsCountByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalsCount.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date createAt = Date.from(cutTime.toInstant());
        int count = masterSignalDao.countCreatedAtMasterSignal(strategyId, createAt);
        assertThat("количество сигналов по стратегии не равно", signalsCount.getValue(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @Test
    @AllureId("829995")
    @DisplayName("C829995.CalculateSignalsCount.Найдена запись в таб. signals_count по ключу: strategy_id , cut, если operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C829995() {
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        //создаем записи по сигналу на разные позиции
        createMasterSignal(31, 1, 2, strategyId, "NOK", "NDS000000001",
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, "ABBV", "NDS000000001",
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, "NOK", "NDS000000001",
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, "ABBV", "NDS000000001",
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        Thread.sleep(5000);
        ByteString strategyIdByte = byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNALS_COUNT,
            strategyIdByte);

        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        await().atMost(TEN_SECONDS).until(() ->
            signalsCount = signalsCountDao.getSignalsCountByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalsCount.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date createAt = Date.from(cutTime.toInstant());
        int count = masterSignalDao.countCreatedAtMasterSignal(strategyId, createAt);
        assertThat("количество сигналов по стратегии не равно", signalsCount.getValue(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем еще сигналы
        createMasterSignal(1, 1, 9, strategyId, "ABBV", "NDS000000001",
            "90.18", "2", 12);
        createMasterSignal(0, 2, 10, strategyId, "NOK", "NDS000000001",
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, "NOK", "NDS000000001",
            "3.09", "4", 12);
        //отправляем событие в топик kafka tracking.analytics.command повторно
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        signalsCount = signalsCountDao.getSignalsCountByStrategyId(strategyId);
        long countRecord = signalsCountDao.count(strategyId);
        assertThat("время cut не равно", countRecord, is(1L));
        assertThat("количество сигналов по стратегии не равно", signalsCount.getValue(), is(count));
    }



    @SneakyThrows
    @Test
    @AllureId("830158")
    @DisplayName("C830158.CalculateSignalsCount.Найдена запись в таб. signals_count по ключу: strategy_id, cut, если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает общее количество сигналов, созданных в рамках торговой стратегии, на заданную метку времени.")
    void C830158() {
        strategyId = UUID.randomUUID();
        log.info("strategyId:  {}", strategyId);
        //создаем записи по сигналу на разные позиции
        createMasterSignal(31, 1, 2, strategyId, "NOK", "NDS000000001",
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, "ABBV", "NDS000000001",
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, "NOK", "NDS000000001",
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, "ABBV", "NDS000000001",
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        Thread.sleep(5000);
        ByteString strategyIdByte = byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNALS_COUNT,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        await().atMost(TEN_SECONDS).until(() ->
            signalsCount = signalsCountDao.getSignalsCountByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(signalsCount.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        Date createAt = Date.from(cutTime.toInstant());
        int count = masterSignalDao.countCreatedAtMasterSignal(strategyId, createAt);
        assertThat("количество сигналов по стратегии не равно", signalsCount.getValue(), is(count));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        //добавляем еще сигналы
        createMasterSignal(1, 1, 9, strategyId, "ABBV", "NDS000000001",
            "90.18", "2", 12);
        createMasterSignal(0, 2, 10, strategyId, "NOK", "NDS000000001",
            "3.17", "4", 12);
        createMasterSignal(0, 1, 11, strategyId, "NOK", "NDS000000001",
            "3.09", "4", 12);
        Thread.sleep(3000);
        //отправляем событие в топик kafka tracking.analytics.command повторно
        OffsetDateTime createTimeNew = OffsetDateTime.now();
        Tracking.AnalyticsCommand reCalculateCommand = steps.createCommandAnalytics(createTimeNew, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.SIGNALS_COUNT,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", reCalculateCommand);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytesNew = reCalculateCommand.toByteArray();
        byteToByteSenderService.send(TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytesNew);
        Thread.sleep(5000);
        long countRecord = signalsCountDao.count(strategyId);
        assertThat("время cut не равно", countRecord, is(1L));
        await().atMost(TEN_SECONDS).until(() ->
            signalsCount = signalsCountDao.getSignalsCountByStrategyId(strategyId), notNullValue());
        LocalDateTime cutNew = LocalDateTime.ofInstant(signalsCount.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommandNew = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        int countNew = masterSignalDao.countCreatedAtMasterSignal(strategyId, createAt);
        assertThat("количество сигналов по стратегии не равно", signalsCount.getValue(), is(countNew));
        assertThat("время cut не равно", true, is(cutNew.equals(cutInCommandNew)));

    }


// методы для работы тестов*************************************************************************

    //методы для перевода стратегии в byte
    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }

    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }



   //методы создает записи по сигналам стратегии
    void createTestDateToMasterSignal(UUID strategyId) {
        createMasterSignal(31, 1, 2, strategyId, "NOK", "NDS000000001",
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, "ABBV", "NDS000000001",
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, "NOK", "NDS000000001",
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, "AAPL", "L01+00000SPB",
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, "ABBV", "NDS000000001",
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, "XS0191754729", "NDS000000001",
            "190.18", "1", 12);
        createMasterSignal(0, 2, 9, strategyId, "NOK", "NDS000000001",
            "3.17", "4", 12);
        createMasterSignal(0, 1, 10, strategyId, "NOK", "NDS000000001",
            "3.09", "4", 12);
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



}
