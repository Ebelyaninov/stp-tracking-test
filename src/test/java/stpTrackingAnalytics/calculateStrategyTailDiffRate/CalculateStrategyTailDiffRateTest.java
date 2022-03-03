package stpTrackingAnalytics.calculateStrategyTailDiffRate;

import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.entities.StrategyTailDiffRate;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.Topics;
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
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("calculateStrategyTailDiffRate Расчет кривизны 'хвоста' стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@Tags({@Tag("stp-tracking-analytics"), @Tag("calculateStrategyTailDiffRate")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAnalyticsStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingInstrumentConfiguration.class
})
public class CalculateStrategyTailDiffRateTest {
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
    StrategyTailDiffRateDao strategyTailDiffRateDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;

    StrategyTailDiffRate strategyTailDiffRate;
    String contractIdMaster;
    SlavePortfolio slavePortfolio;
    Client clientSlave;

    String contractIdSlaveOne;
    String contractIdSlaveTwo;
    String contractIdSlaveThree;

    UUID investIdSlaveOne;
    UUID investIdSlaveTwo;
    UUID investIdSlaveThree;

    UUID strategyId;
    UUID investIdMaster;

    String description = "new test стратегия autotest";


    List<Float> strategyTailDiffRateQuantiles = List.of((float) 0.0, (float)0.5, (float)0.9, (float)0.99, (float)1.0);


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlaveOne));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlaveOne));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlaveOne));
            } catch (Exception e) {
            }
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlaveTwo));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlaveTwo));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlaveTwo));
            } catch (Exception e) {
            }
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlaveThree));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlaveThree));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlaveThree));
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlaveOne, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlaveTwo, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlaveThree, strategyId);
            } catch (Exception e) {
            }

            try {
                strategyTailDiffRateDao.deleteStrategyTailDiffRateByStrategyId(strategyId);
            } catch (Exception e) {
            }
        });
    }

    @BeforeAll
    void getDataClients() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdMasterAnalytics);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlaveOne = steps.getBrokerAccounts(siebel.siebelIdAnalyticsSlaveOne);
        investIdSlaveOne = resAccountSlaveOne.getInvestId();
        contractIdSlaveOne = resAccountSlaveOne.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlaveTwo = steps.getBrokerAccounts(siebel.siebelIdAnalyticsSlaveTwo);
        investIdSlaveTwo = resAccountSlaveTwo.getInvestId();
        contractIdSlaveTwo = resAccountSlaveTwo.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlaveThree = steps.getBrokerAccounts(siebel.siebelIdAnalyticsSlaveThree);
        investIdSlaveThree = resAccountSlaveThree.getInvestId();
        contractIdSlaveThree = resAccountSlaveThree.getBrokerAccounts().get(0).getId();

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
    @AllureId("1530868")
    @DisplayName("C1530868.CalculateStrategyTailDiffRate.Пересчитывает кривизну 'хвоста' стратегии на заданную метку времени")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1530868(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOne();
        createSlavePortfolioTwo();
        createSlavePortfolioThree();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<Float> diffRates = new ArrayList();
        //рассчитываем portfolioDiffRate для каждого подписчика
        BigDecimal portfolioDiffRateOne = getPortfolioDiffRate(contractIdSlaveOne, strategyId, 4);
        BigDecimal portfolioDiffRateTwo = getPortfolioDiffRate(contractIdSlaveTwo, strategyId, 4);
        BigDecimal portfolioDiffRateThree = getPortfolioDiffRate(contractIdSlaveThree, strategyId, 4);
        //записываем в список полученные значения
        diffRates.add(portfolioDiffRateOne.floatValue());
        diffRates.add(portfolioDiffRateTwo.floatValue());
        diffRates.add(portfolioDiffRateThree.floatValue());
        //рассчитываем кривизну и записываем результат
        Map<Float, Float> result =  calculateQuantiles(strategyTailDiffRateQuantiles, diffRates);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }






    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1532791")
    @DisplayName("C1532791.CalculateStrategyTailDiffRate.Пересчитывает кривизну 'хвоста' стратегии на заданную метку времени." +
        "Сhanged_at <= cut")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1532791(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOne();
        createSlavePortfolioTwo();
        createSlavePortfolioThree();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(7);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<Float> diffRates = new ArrayList();
        //рассчитываем portfolioDiffRate для каждого подписчика
        BigDecimal portfolioDiffRateOne = getPortfolioDiffRate(contractIdSlaveOne, strategyId, 3);
        BigDecimal portfolioDiffRateTwo = getPortfolioDiffRate(contractIdSlaveTwo, strategyId, 3);
        BigDecimal portfolioDiffRateThree = getPortfolioDiffRate(contractIdSlaveThree, strategyId, 3);
        //записываем в список полученные значения
        diffRates.add(portfolioDiffRateOne.floatValue());
        diffRates.add(portfolioDiffRateTwo.floatValue());
        diffRates.add(portfolioDiffRateThree.floatValue());
        //рассчитываем кривизну и записываем результат
        Map<Float, Float> result =  calculateQuantiles(strategyTailDiffRateQuantiles, diffRates);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }




    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1530698")
    @DisplayName("C1530698.CalculateStrategyTailValue.Портфель slave попадает на заданную метку времени среза")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1530698(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Map<Float, Float> result = new LinkedHashMap<>();
        result.put((float)0.0, (float)0.0);
        result.put((float)0.5, (float)0.0);
        result.put((float)0.9, (float)0.0);
        result.put((float)0.99, (float)0.0);
        result.put((float)1.0, (float)0.0);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1531377")
    @DisplayName("C1531377.CalculateStrategyTailDiffRate.Не найдена запись по портфелю в changed_at_slave_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1531377(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOne();
        createSlavePortfolioTwo();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<Float> diffRates = new ArrayList();
        //рассчитываем portfolioDiffRate для каждого подписчика
        BigDecimal portfolioDiffRateOne = getPortfolioDiffRate(contractIdSlaveOne, strategyId, 4);
        BigDecimal portfolioDiffRateTwo = getPortfolioDiffRate(contractIdSlaveTwo, strategyId, 4);
//        BigDecimal portfolioDiffRateThree = getPortfolioDiffRate(contractIdSlaveThree, strategyId, 4);
        //записываем в список полученные значения
        diffRates.add(portfolioDiffRateOne.floatValue());
        diffRates.add(portfolioDiffRateTwo.floatValue());
//        diffRates.add(portfolioDiffRateThree.floatValue());
        //рассчитываем кривизну и записываем результат
        Map<Float, Float> result =  calculateQuantiles(strategyTailDiffRateQuantiles, diffRates);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1531774")
    @DisplayName("C1531774.CalculateStrategyTailDiffRate.Пересчитывает кривизну 'хвоста' стратегии на заданную метку времени.PortfolioValue = 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1531774(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOne();
        createSlavePortfolioTwo();
        createSlavePortfolioThreePortfolioValue();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<Float> diffRates = new ArrayList();
        //рассчитываем portfolioDiffRate для каждого подписчика
        BigDecimal portfolioDiffRateOne = getPortfolioDiffRate(contractIdSlaveOne, strategyId, 4);
        BigDecimal portfolioDiffRateTwo = getPortfolioDiffRate(contractIdSlaveTwo, strategyId, 4);
        BigDecimal portfolioDiffRateThree = BigDecimal.ZERO;
        //записываем в список полученные значения
        diffRates.add(portfolioDiffRateOne.floatValue());
        diffRates.add(portfolioDiffRateTwo.floatValue());
        diffRates.add(portfolioDiffRateThree.floatValue());
        //рассчитываем кривизну и записываем результат
        Map<Float, Float> result =  calculateQuantiles(strategyTailDiffRateQuantiles, diffRates);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1534083")
    @DisplayName("C1534083.CalculateStrategyTailDiffRate.Пересчитывает кривизну 'хвоста' стратегии на заданную метку времени.Нет позиций")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1534083(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOnePortfolioValue();
        createSlavePortfolioTwo();
        createSlavePortfolioThree();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<Float> diffRates = new ArrayList();
        //рассчитываем portfolioDiffRate для каждого подписчика
        BigDecimal portfolioDiffRateOne = BigDecimal.ZERO;
        BigDecimal portfolioDiffRateTwo = getPortfolioDiffRate(contractIdSlaveTwo, strategyId, 4);
        BigDecimal portfolioDiffRateThree = getPortfolioDiffRate(contractIdSlaveThree, strategyId, 4);
        //записываем в список полученные значения
        diffRates.add(portfolioDiffRateOne.floatValue());
        diffRates.add(portfolioDiffRateTwo.floatValue());
        diffRates.add(portfolioDiffRateThree.floatValue());
        //рассчитываем кривизну и записываем результат
        Map<Float, Float> result =  calculateQuantiles(strategyTailDiffRateQuantiles, diffRates);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }




    @SneakyThrows
    @Test
    @AllureId("1533651")
    @DisplayName("C1533651.CalculateStrategyTailDiffRate.Пересчитывает кривизну 'хвоста' стратегии на заданную метку времени." +
        "Найдена запись в в таблице strategy_tail_diff_rate.CALCULATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1533651() {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOne();
        createSlavePortfolioTwo();
        createSlavePortfolioThree();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        createDateStrategyTailDiffRate(strategyId, Date.from(cutTime.toInstant()));
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Map<Float, Float> result = new LinkedHashMap<>();
        result.put((float)0.0, (float)0.01);
        result.put((float)0.5, (float)0.1);
        result.put((float)0.9, (float)0.55);
        result.put((float)0.99, (float)0.55);
        result.put((float)1.0, (float)0.55);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }


    @SneakyThrows
    @Test
    @AllureId("1533734")
    @DisplayName("C1533734.CalculateStrategyTailDiffRate.Пересчитывает кривизну 'хвоста' стратегии на заданную метку времени." +
        "Найдена запись в в таблице strategy_tail_diff_rate.RECALCULATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1533734() {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlaveOne, contractIdSlaveOne, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveTwo, contractIdSlaveTwo, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        steps.createSubcriptionWithBlocked(investIdSlaveThree, contractIdSlaveThree, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioOne();
        createSlavePortfolioTwo();
        createSlavePortfolioThree();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        createDateStrategyTailDiffRate(strategyId, Date.from(cutTime.toInstant()));
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_DIFF_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        List<Float> diffRates = new ArrayList();
        //рассчитываем portfolioDiffRate для каждого подписчика
        BigDecimal portfolioDiffRateOne = getPortfolioDiffRate(contractIdSlaveOne, strategyId, 4);
        BigDecimal portfolioDiffRateTwo = getPortfolioDiffRate(contractIdSlaveTwo, strategyId, 4);
        BigDecimal portfolioDiffRateThree = getPortfolioDiffRate(contractIdSlaveThree, strategyId, 4);
        //записываем в список полученные значения
        diffRates.add(portfolioDiffRateOne.floatValue());
        diffRates.add(portfolioDiffRateTwo.floatValue());
        diffRates.add(portfolioDiffRateThree.floatValue());
        //рассчитываем кривизну и записываем результат
        Map<Float, Float> result =  calculateQuantiles(strategyTailDiffRateQuantiles, diffRates);
        checkStrategyTailDiffRate(strategyId);
        await().atMost(TEN_SECONDS).until(() ->
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId), notNullValue());
        assertThat("значение в каждом квантиле не равно", strategyTailDiffRate.getValues(), is(result));
    }







    void createSlavePortfolioOne() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "30000";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlaveOne, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "26710.3";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "10", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), new BigDecimal("328.97"), new BigDecimal("26.5669"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveOne, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        String baseMoneySlaveTwo = "13525.969";
        List<SlavePortfolio.Position> positionListTwoPos = steps.createListSlavePositionWithTwoPos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "50", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            "3", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), new BigDecimal("321.6"),
            new BigDecimal("106.777"), new BigDecimal("4.7578"), new BigDecimal("-2.9997"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveOne, strategyId, 3, 3,
            baseMoneySlaveTwo, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), positionListTwoPos);
        String baseMoneySlaveThree = "871.169";
        List<SlavePortfolio.Position> positionListTwoThree = steps.createListSlavePositionWithThreePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "50", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()),instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            "3", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), instrument.tickerLKOH, instrument.tradingClearingAccountLKOH,
            "2", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()),  new BigDecimal("321.6"),
            new BigDecimal("106.777"), new BigDecimal("6550"), new BigDecimal("-9.2838"), new BigDecimal("-3.0003"),
            new BigDecimal("-1.9935"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveOne, strategyId, 4, 4,
            baseMoneySlaveThree, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionListTwoThree);
    }




    void createSlavePortfolioTwo() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "29576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlaveTwo, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "28126.23";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), new BigDecimal("321.6"), new BigDecimal("-0.8132"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveTwo, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        String baseMoneySlaveTwo = "27806.13";
        List<SlavePortfolio.Position> positionListTwoPos = steps.createListSlavePositionWithTwoPos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), new BigDecimal("321.6"),
            new BigDecimal("106.777"), new BigDecimal("-0.0392"), new BigDecimal("-0.003"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveTwo, strategyId, 3, 3,
            baseMoneySlaveTwo, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), positionListTwoPos);
        String baseMoneySlaveThree = "4193.13";
        List<SlavePortfolio.Position> positionListTwoThree = steps.createListSlavePositionWithThreePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), instrument.tickerLKOH, instrument.tradingClearingAccountLKOH,
            steps.quantityLKOH, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()),  new BigDecimal("321.6"),
            new BigDecimal("106.777"), new BigDecimal("6550"), new BigDecimal("-0.1247"), new BigDecimal("-0.0069"),
            new BigDecimal("0.99"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveTwo, strategyId, 4, 4,
            baseMoneySlaveThree, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionListTwoThree);
    }

    void createSlavePortfolioThree() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "29576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlaveThree, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "28126.23";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), new BigDecimal("321.6"), new BigDecimal("-0.8132"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveThree, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        String baseMoneySlaveTwo = "27806.13";
        List<SlavePortfolio.Position> positionListTwoPos = steps.createListSlavePositionWithTwoPos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), new BigDecimal("321.6"),
            new BigDecimal("106.777"), new BigDecimal("-0.0392"), new BigDecimal("-0.003"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveThree, strategyId, 3, 3,
            baseMoneySlaveTwo, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), positionListTwoPos);
        String baseMoneySlaveThree = "4193.13";
        List<SlavePortfolio.Position> positionListTwoThree = steps.createListSlavePositionWithThreePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), instrument.tickerLKOH, instrument.tradingClearingAccountLKOH,
            steps.quantityLKOH, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()),  new BigDecimal("321.6"),
            new BigDecimal("106.777"), new BigDecimal("6550"), new BigDecimal("-0.1247"), new BigDecimal("-0.0069"),
            new BigDecimal("0.33"));
        steps.createSlavePortfolioWithPosition(contractIdSlaveThree, strategyId, 4, 4,
            baseMoneySlaveThree, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionListTwoThree);
    }


    void createSlavePortfolioThreePortfolioValue() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);

        String baseMoneySlave = "0";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlaveThree, strategyId, 1, 4,
            baseMoneySlave, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionList);
    }

    void createSlavePortfolioOnePortfolioValue() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);

        String baseMoneySlave = "100";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlaveOne, strategyId, 1, 4,
            baseMoneySlave, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionList);
    }




    BigDecimal getPortfolioDiffRate (String contractIdSlave, UUID strategyId, int version ) {
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, version);
        BigDecimal portfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal portfolioDiffRate = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            //для каждой позиции рассчитываем кривизну positionDiffRate
            BigDecimal positionDiffRate = slavePortfolio.getPositions().get(i)
                .getQuantityDiff().multiply(slavePortfolio.getPositions().get(i).getPrice()).abs();
            //определяем стоимость портфеля на момент сравнения
            portfolioValue = portfolioValue.add(slavePortfolio.getPositions().get(i).getQuantity()
                .multiply(slavePortfolio.getPositions().get(i).getPrice()));
            // сумма (рассчитанные positionDiffRate по каждой позиции в портфеле) / portfolioValue
            portfolioDiffRate = portfolioDiffRate.add(positionDiffRate);
        }
        //определяем кривизну портфеля portfolioDiffRate
        portfolioDiffRate = portfolioValue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : portfolioDiffRate.divide(portfolioValue, 2, RoundingMode.HALF_UP);

        return portfolioDiffRate;
    }


    public Map<Float, Float> calculateQuantiles(List<Float> probabilities, List<Float> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        Collections.sort(data);
        Map<Float, Float> result = new HashMap<>();
        for (Float probability : probabilities) {
            result.put(probability, calculateQuantile(probability, data));
        }
        return result;
    }

    /**
     * @param data sorted list
     */
    private Float calculateQuantile(Float probability, List<Float> data) {
        if (probability > 1 || probability < 0) {
            throw new IllegalArgumentException("Incorrect probability value");
        }
        if (probability == 0) {
            return data.get(0);
        }
        if (probability == 1.0) {
            return data.get(data.size() - 1);
        }
        var index = (int) Math.ceil(probability * data.size()) - 1;
        return data.get(index);
    }


    void checkStrategyTailDiffRate(UUID strategyId) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3000);
            strategyTailDiffRate = strategyTailDiffRateDao.getStrategyTailDiffRateByStrategyId(strategyId);
            if (strategyTailDiffRate == null) {
                Thread.sleep(5000);
            } else {
                break;
            }
        }
    }


    void createDateStrategyTailDiffRate(UUID strategyId, Date date) {
        Map<Float, Float> values = new LinkedHashMap<>();
        values.put((float)0.0, (float)0.01);
        values.put((float)0.5, (float)0.1);
        values.put((float)0.9, (float)0.55);
        values.put((float)0.99, (float)0.55);
        values.put((float)1.0, (float)0.55);
        strategyTailDiffRate = StrategyTailDiffRate.builder()
            .strategyId(strategyId)
            .cut(date)
            .values(values)
            .build();
        strategyTailDiffRateDao.insertIntoStrategyTailDiffRate(strategyTailDiffRate);
    }






}
