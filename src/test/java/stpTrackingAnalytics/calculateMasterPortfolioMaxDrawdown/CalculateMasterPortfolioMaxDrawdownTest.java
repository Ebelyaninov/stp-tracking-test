package stpTrackingAnalytics.calculateMasterPortfolioMaxDrawdown;

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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Pair;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioMaxDrawdown;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


@Slf4j
@Epic("calculateMasterPortfolioMaxDrawdown Пересчет максимальной просадки master-портфеля")
@Feature("TAP-9597")
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
public class CalculateMasterPortfolioMaxDrawdownTest {
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingAnalyticsSteps steps;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterPortfolioMaxDrawdownDao masterPortfolioMaxDrawdownDao;


    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    String contractIdMaster;
    MasterPortfolioValue masterPortfolioValue;
    MasterPortfolioMaxDrawdown masterPortfolioMaxDrawdown;


    String SIEBEL_ID_MASTER = "5-192WBUXCI";

    UUID strategyId;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
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
                masterPortfolioMaxDrawdownDao.deleteMasterPortfolioMaxDrawdownByStrategyId(strategyId);
            } catch (Exception e) {
            }

        });
    }


    private static Stream<Arguments> provideAnalyticsCommand() {
        return Stream.of(
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE),
            Arguments.of(Tracking.AnalyticsCommand.Operation.RECALCULATE)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("983303")
    @DisplayName("C983303.CalculateMasterPortfolioMaxDrawdown.Пересчет максимальной просадки master-портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает максимальную просадку master-портфеля владельца стратегии на заданную метку времени.")
    void C983303(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
//        String baseMoney = "16551.10";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        createDateMasterPortfolioValue(strategyId, 31, 3, "174478.05");
        createDateMasterPortfolioValue(strategyId, 25, 2, "198478.67");
        createDateMasterPortfolioValue(strategyId, 29, 4, "304896.31");
        createDateMasterPortfolioValue(strategyId, 15, 1, "199580.35");
        createDateMasterPortfolioValue(strategyId, 12, 4, "283895.42");
        createDateMasterPortfolioValue(strategyId, 10, 1, "177213.69");
        createDateMasterPortfolioValue(strategyId, 7, 1, "77886.12");
        createDateMasterPortfolioValue(strategyId, 5, 3, "96845.36");
        createDateMasterPortfolioValue(strategyId, 4, 2, "103491.11");
        createDateMasterPortfolioValue(strategyId, 3, 5, "107269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "112684.75");
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_MAX_DRAWDOWN,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Date start = Date.from(cutTime.minusDays(365).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<BigDecimal> masterPortfolioValues = masterPortfolioValueDao
            .getMasterPortfolioValuesByStrategyId(strategyId, start, end).stream()
            .sorted((p1, p2) -> p1.getFirst().compareTo(p2.getFirst()))
            .map(Pair::getSecond)
            .collect(Collectors.toList());

        BigDecimal maxValue = masterPortfolioValues.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal value : masterPortfolioValues) {
            if (value.compareTo(maxValue) >= 0) {
                maxValue = value;
            } else {
                var drawdown = (BigDecimal.ONE.subtract(value.divide(maxValue, 2, RoundingMode.HALF_UP))).scaleByPowerOfTen(2);
                maxDrawdown = maxDrawdown.max(drawdown);
            }
        }
        log.info("Mакс. просадка master-портфеля:  {}", maxDrawdown);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioMaxDrawdown = masterPortfolioMaxDrawdownDao.getMasterPortfolioMaxDrawdownByStrategyId(strategyId), notNullValue());
        //проверяем параметры
        checkParam(maxDrawdown, cutTime);
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("983084")
    @DisplayName("C983084.CalculateMasterPortfolioMaxDrawdown.Пересчет максимальной просадки master-портфеля," +
        " Выбор данных по условию")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает максимальную просадку master-портфеля владельца стратегии на заданную метку времени.")
    void C983084(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());

        createDateMasterPortfolioValue(strategyId, 370, 3, "174478.05");
        createDateMasterPortfolioValue(strategyId, 369, 2, "198478.67");
        createDateMasterPortfolioValue(strategyId, 368, 4, "304896.31");
        createDateMasterPortfolioValue(strategyId, 15, 1, "199580.35");
        createDateMasterPortfolioValue(strategyId, 12, 4, "305896.19");
        createDateMasterPortfolioValue(strategyId, 10, 1, "303741.23");
        createDateMasterPortfolioValue(strategyId, 7, 1, "77886.12");
        createDateMasterPortfolioValue(strategyId, 5, 3, "96845.36");
        createDateMasterPortfolioValue(strategyId, 4, 2, "103491.11");
        createDateMasterPortfolioValue(strategyId, 3, 5, "107269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "112684.75");

        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(4);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_MAX_DRAWDOWN,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Date start = Date.from(cutTime.minusDays(365).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<BigDecimal> masterPortfolioValues = masterPortfolioValueDao
            .getMasterPortfolioValuesByStrategyId(strategyId, start, end).stream()
            .sorted((p1, p2) -> p1.getFirst().compareTo(p2.getFirst()))
            .map(Pair::getSecond)
            .collect(Collectors.toList());

        BigDecimal maxValue = masterPortfolioValues.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal value : masterPortfolioValues) {
            if (value.compareTo(maxValue) >= 0) {
                maxValue = value;
            } else {
                var drawdown = (BigDecimal.ONE.subtract(value.divide(maxValue, 2, RoundingMode.HALF_UP))).scaleByPowerOfTen(2);
                maxDrawdown = maxDrawdown.max(drawdown);
            }
        }
        log.info("Mакс. просадка master-портфеля:  {}", maxDrawdown);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioMaxDrawdown = masterPortfolioMaxDrawdownDao.getMasterPortfolioMaxDrawdownByStrategyId(strategyId), notNullValue());
        //проверяем параметры
        checkParam(maxDrawdown, cutTime);
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("983216")
    @DisplayName("C983216.CalculateMasterPortfolioMaxDrawdown.Пересчет максимальной просадки master-портфеля," +
        " ни одной записи не найдено")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает максимальную просадку master-портфеля владельца стратегии на заданную метку времени.")
    void C983216(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        createDateMasterPortfolioValue(strategyId, 370, 3, "174478.05");
        createDateMasterPortfolioValue(strategyId, 369, 2, "198478.67");
        createDateMasterPortfolioValue(strategyId, 3, 5, "107269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "112684.75");
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(4);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_MAX_DRAWDOWN,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Date start = Date.from(cutTime.minusDays(365).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<BigDecimal> masterPortfolioValues = masterPortfolioValueDao
            .getMasterPortfolioValuesByStrategyId(strategyId, start, end).stream()
            .sorted((p1, p2) -> p1.getFirst().compareTo(p2.getFirst()))
            .map(Pair::getSecond)
            .collect(Collectors.toList());
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        log.info("Mакс. просадка master-портфеля:  {}", maxDrawdown);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioMaxDrawdown = masterPortfolioMaxDrawdownDao.getMasterPortfolioMaxDrawdownByStrategyId(strategyId), notNullValue());
        //проверяем параметры
        checkParam(maxDrawdown, cutTime);
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("980851")
    @DisplayName("C980851.CalculateMasterPortfolioMaxDrawdown.ППересчет максимальной просадки master-портфеля," +
        " определяем логику расчета в зависимости от параметра operation")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает максимальную просадку master-портфеля владельца стратегии на заданную метку времени.")
    void C980851(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        createDateMasterPortfolioValue(strategyId, 31, 3, "174478.05");
        createDateMasterPortfolioValue(strategyId, 25, 2, "198478.67");
        createDateMasterPortfolioValue(strategyId, 15, 4, "178475.64");
        createDateMasterPortfolioValue(strategyId, 15, 1, "199580.35");
        createDateMasterPortfolioValue(strategyId, 12, 4, "176315.88");
        createDateMasterPortfolioValue(strategyId, 10, 1, "177213.69");
        createDateMasterPortfolioValue(strategyId, 7, 1, "177868.12");
        createDateMasterPortfolioValue(strategyId, 5, 3, "196845.36");
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(4);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_MAX_DRAWDOWN,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Date start = Date.from(cutTime.minusDays(365).toInstant());
        Date end = Date.from(cutTime.toInstant());
        List<BigDecimal> masterPortfolioValues = masterPortfolioValueDao
            .getMasterPortfolioValuesByStrategyId(strategyId, start, end).stream()
            .sorted((p1, p2) -> p1.getFirst().compareTo(p2.getFirst()))
            .map(Pair::getSecond)
            .collect(Collectors.toList());
        BigDecimal maxValue = masterPortfolioValues.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal value : masterPortfolioValues) {
            if (value.compareTo(maxValue) >= 0) {
                maxValue = value;
            } else {
                var drawdown = (BigDecimal.ONE.subtract(value.divide(maxValue, 2, RoundingMode.HALF_UP))).scaleByPowerOfTen(2);
                maxDrawdown = maxDrawdown.max(drawdown);
            }
        }
        log.info("Mакс. просадка master-портфеля:  {}", maxDrawdown);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioMaxDrawdown = masterPortfolioMaxDrawdownDao.getMasterPortfolioMaxDrawdownByStrategyId(strategyId), notNullValue());
        //проверяем параметры
        checkParam(maxDrawdown, cutTime);
        createDateMasterPortfolioValue(strategyId, 4, 2, "683491.11");
        createDateMasterPortfolioValue(strategyId, 3, 5, "87269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "982684.75");
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<BigDecimal> masterPortfolioValuesNew = masterPortfolioValueDao
            .getMasterPortfolioValuesByStrategyId(strategyId, start, end).stream()
            .sorted((p1, p2) -> p1.getFirst().compareTo(p2.getFirst()))
            .map(Pair::getSecond)
            .collect(Collectors.toList());
        BigDecimal maxValueNew = masterPortfolioValuesNew.get(0);
        BigDecimal maxDrawdownNew = BigDecimal.ZERO;
        for (BigDecimal value : masterPortfolioValuesNew) {
            if (value.compareTo(maxValueNew) >= 0) {
                maxValueNew = value;
            } else {
                var drawdownNew = (BigDecimal.ONE.subtract(value.divide(maxValueNew, 2, RoundingMode.HALF_UP))).scaleByPowerOfTen(2);
                maxDrawdownNew = maxDrawdownNew.max(drawdownNew);
            }
        }
        log.info("Mакс. просадка master-портфеля:  {}", maxDrawdownNew);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioMaxDrawdown = masterPortfolioMaxDrawdownDao.getMasterPortfolioMaxDrawdownByStrategyId(strategyId), notNullValue());
        //проверяем параметры
        checkParam(maxDrawdownNew, cutTime);

    }

//методы для работы тестов****************************************************************************

    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }

    void checkParam(BigDecimal maxDrawdown, OffsetDateTime cutTime) {
        assertThat("value просадки портфеля не равно", masterPortfolioMaxDrawdown.getValue(), is(maxDrawdown));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioMaxDrawdown.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }

}
