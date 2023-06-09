package stpTrackingAnalytics.calculateStrategyTailValue;

import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
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
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;
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
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("calculateStrategyTailValue Пересчет объема хвоста стратегии")
@Feature("AP-9362")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@Tags({@Tag("stp-tracking-analytics"), @Tag("calculateStrategyTailValue")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAnalyticsStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    ApiCreatorConfiguration.class
})
public class CalculateStrategyTailValueTest {
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
    SlaveOrderDao slaveOrderDao;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingAnalyticsSteps steps;
    @Autowired
    StrategyTailValueDao strategyTailValueDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;

    StrategyTailValue strategyTailValue;
    String contractIdMaster;


    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();


    Client clientSlave;
    String siebelIdSlave2 = "5-7ECGV169";
    String contractIdSlave;
    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;

    String description = "new test стратегия autotest";

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

    private static Stream<Arguments> provideAnalyticsCommand() {
        return Stream.of(
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE, StrategyStatus.active),
            Arguments.of(Tracking.AnalyticsCommand.Operation.RECALCULATE, StrategyStatus.frozen)
        );
    }

    BigDecimal minPriceIncrement = new BigDecimal("0.001");

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1316557")
    @DisplayName("C1316557.CalculateStrategyTailValue.Портфель slave попадает на заданную метку времени среза")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1316557(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolio();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        // формируем список позиций для запроса prices MD
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        instrumentList.add(instrument.instrumentLKOH);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime.minusHours(3));
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);

        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 3);
        // получаем данные для расчета по облигациям
        List<String> getDateFromFireg = getIntrumentdate(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getDateFromFireg.get(0);
        String nominal = getDateFromFireg.get(1);
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3)
            .add(new BigDecimal("4193.13"));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(valuePortfolio));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1351693")
    @DisplayName("C1351693.CalculateStrategyTailValue.Сhanged_at <= cut")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1351693(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        createSlavePortfolio();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(7);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        // формируем список позиций для запроса prices MD
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime.minusHours(3));
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        List<String> getDateFromFireg = getIntrumentdate(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getDateFromFireg.get(0);
        String nominal = getDateFromFireg.get(1);
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(new BigDecimal("27806.13"));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(valuePortfolio));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1352912")
    @DisplayName("C1352912.CalculateStrategyTailValue.Сhanged_at <= cut.EndSubscribtion")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1352912(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        createSlavePortfolio();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(7);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        // формируем список позиций для запроса prices MD
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime.minusHours(3));
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        List<String> getDateFromFireg = getIntrumentdate(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getDateFromFireg.get(0);
        String nominal = getDateFromFireg.get(1);
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(new BigDecimal("27806.13"));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(valuePortfolio));
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(valuePortfolio));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("983303")
    @DisplayName("C983303.CalculateStrategyTailValue.Пересчет объема хвоста стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C983303_444(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        createSlavePortfolio();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(1);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(new BigDecimal("0")));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1352954")
    @DisplayName("C1352954.CalculateStrategyTailValue.Ни одной подписки не было найдено, totalValue = 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1352954(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), null);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(new BigDecimal("0")));
    }


    @SneakyThrows
    @Test
    @AllureId("1354173")
    @DisplayName("C1354173.CalculateStrategyTailValue.На заданную метку времени показатель уже рассчитывался")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1354173() {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        createSlavePortfolio();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "71815.72");
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(new BigDecimal("71815.72")));
    }

    @SneakyThrows
    @Test
    @AllureId("1354465")
    @DisplayName("C1354465.CalculateStrategyTailValue.Расчет стоимости виртуального портфеля," +
        " если operation = 'CALCULATE' позиция не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C1354465() {
        String ticker1 = "TEST";
        String tradingClearingAccount1 = "L01+00000F00";
        String quantity1 = "50";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.frozen, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "29576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "28126.23";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            quantity1, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(new BigDecimal(baseMoneySlaveOne)));
    }


    @SneakyThrows
    @Test
    @AllureId("1354512")
    @DisplayName("C1354512.CalculateStrategyTailValue.Позиция не найдена в instrumentPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C1354512() {
        String ticker1 = "FXITTEST";
        String tradingClearingAccount1 = "L01+00002F00";
        String quantity1 = "500";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "29576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "28126.23";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePosLight(ticker1, tradingClearingAccount1,
            quantity1, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(new BigDecimal(baseMoneySlaveOne)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1788035")
    @DisplayName("C1788035.CalculateStrategyTailValue.Портфель slave попадает на заданную метку времени среза." +
        "Массива positions обрабатываемого портфеля, у которого quantity <= 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает объем хвоста обрабатываемой стратегии (стоимость всех slave-портфелей, подписанных на нее) на заданную метку времени.")
    void C1788035(Tracking.AnalyticsCommand.Operation operation) {
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.frozen, 0, LocalDateTime.now(), null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(20);
        steps.createSubcriptionWithBlocked(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель для ведомого
        createSlavePortfolioWithZero();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.STRATEGY_TAIL_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        // формируем список позиций для запроса prices MD
        List<String> instrumentList = new ArrayList<>();
//        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        instrumentList.add(instrument.instrumentLKOH);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime.minusHours(3));
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);

        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        List<String> getDateFromFireg = getIntrumentdate(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getDateFromFireg.get(0);
        String nominal = getDateFromFireg.get(1);
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3)
            .add(new BigDecimal("3993.13"));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofSeconds(3)).until(() ->
            strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId), notNullValue());
        strategyTailValue = strategyTailValueDao.getStrategyTailValueByStrategyId(strategyId);
        assertThat("value стоимости портфеля не равно", strategyTailValue.getValue(), is(valuePortfolio));
    }


    List<String> getIntrumentdate(String ticker, String classCode, String date) {
        List<String> dateFromFireg = new ArrayList<>();
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker)
            .idKindQuery("ticker")
            .classCodeQuery(classCode)
            .startDateQuery(date)
            .endDateQuery(date)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        dateFromFireg.add(aciValue);
        dateFromFireg.add(nominal);
        return dateFromFireg;
    }

    void createSlavePortfolio() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "29576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "28126.23";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        String baseMoneySlaveTwo = "27806.13";
        List<SlavePortfolio.Position> positionListTwoPos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            baseMoneySlaveTwo, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), positionListTwoPos);
        String baseMoneySlaveThree = "4193.13";
        List<SlavePortfolio.Position> positionListTwoThree = steps.createListSlavePositionWithThreePosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), instrument.tickerLKOH, instrument.tradingClearingAccountLKOH,
            steps.quantityLKOH, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 4, 4,
            baseMoneySlaveThree, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionListTwoThree);
    }


    void createSlavePortfolioWithZero() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        Date date = Date.from(utc.toInstant());
        String baseMoneySlave = "29576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        String baseMoneySlaveOne = "28126.23";
        List<SlavePortfolio.Position> positionListOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySlaveOne, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), positionListOnePos);
        String baseMoneySlaveTwo = "27806.13";
        List<SlavePortfolio.Position> positionListTwoPos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            baseMoneySlaveTwo, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), positionListTwoPos);
        String baseMoneySlaveThree = "4193.13";
        List<SlavePortfolio.Position> positionListTwoThree = steps.createListSlavePositionWithThreePosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBER, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), instrument.tickerLKOH, instrument.tradingClearingAccountLKOH,
            steps.quantityLKOH, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 4, 4,
            baseMoneySlaveThree, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), positionListTwoThree);
        String baseMoneySlaveFour = "3993.13";
        List<SlavePortfolio.Position> positionListFour = steps.createListSlavePositionWithFourPosLight(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            steps.quantitySBERZero, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            steps.quantitySU29009RMFS6, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).toInstant()), instrument.tickerLKOH, instrument.tradingClearingAccountLKOH,
            steps.quantityLKOH, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerESGR, instrument.tradingClearingAccountESGR,
            steps.quantityESGRZero, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 5, 4,
            baseMoneySlaveFour, Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()), positionListFour);
    }




    void createDateStrategyTailValue(UUID strategyId, Date date, String value) {
        strategyTailValue = StrategyTailValue.builder()
            .strategyId(strategyId)
            .cut(date)
            .value(new BigDecimal(value))
            .build();
        strategyTailValueDao.insertIntoStrategyTailValue(strategyTailValue);
    }

}
