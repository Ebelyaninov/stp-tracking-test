package stpTrackingAnalytics.calculateMasterPortfolioValue;

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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingAnalyticsStepsConfiguration;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingAnalyticsSteps.StpTrackingAnalyticsSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("CalculateMasterPortfolioValue - Расчет стоимости виртуального портфеля")
@Feature("TAP-9016")
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
public class CalculateMasterPortfolioValueTest {

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

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();

    MasterPortfolioValue masterPortfolioValue;
    String contractIdMaster;
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
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioValueDao.deleteMasterPortfolioValueByStrategyId(strategyId);
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
    @AllureId("836966")
    @DisplayName("C836966.CalculateMasterPortfolioValue.Расчет стоимости виртуального портфеля, " +
        "если operation = 'CALCULATE', отсутствуют позиции в портфеле")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C836966(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String baseMoney = "16551.10";
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
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        OffsetDateTime timeChangedAt = OffsetDateTime.now();
        Date changedAt = Date.from(timeChangedAt.minusDays(5).toInstant());
        // создаем портфель с пустыми позициями
        steps.createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, 4, baseMoney, changedAt);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE, strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        BigDecimal valuePortfolio = (new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("842614")
    @DisplayName("C842614.CalculateMasterPortfolioValue.Расчет стоимости виртуального портфеля," +
        " находим виртуальный портфель на заданную метку времени среза")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C842614(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        String baseMoney = "73445.55";
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
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(9);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE, strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);
        // формируем список позиций для запроса prices MD
        String ListInst = steps.instrumet1 + "," + steps.instrumet2 + "," + steps.instrumet3 + "," + steps.instrumet4;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 4);
        // получаем данные для расчета по облигациям
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(steps.ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(steps.classCode2)
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
        BigDecimal valuePos4 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(steps.instrumet3)) {
                valuePos3 = new BigDecimal(steps.quantity3).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet4)) {
                valuePos4 = new BigDecimal(steps.quantity4).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(valuePos3).add(valuePos4)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("842615")
    @DisplayName("C842615.CalculateMasterPortfolioValue.Расчет стоимости виртуального портфеля" +
        " с разными инструментами: share, bond, etf, money")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C842615(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String baseMoney = "16551.10";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
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
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE, strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateTs = fmt.format(cutTime);
        String dateFireg = fmtFireg.format(cutTime);
        // формируем список позиций для запроса prices MD
        String ListInst = steps.instrumet1 + "," + steps.instrumet2 + "," + steps.instrumet3 + "," + steps.instrumet4 + "," +
            steps.instrumet5 + "," + steps.instrumet6 + "," + steps.instrumet7;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 7);
        // получаем данные для расчета по облигациям
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(steps.ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(steps.classCode2)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePortfolio = steps.getValuePortfolio(pricesPos, nominal,
            minPriceIncrement, aciValue, baseMoney);
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @Test
    @AllureId("838562")
    @DisplayName("C838562.CalculateMasterPortfolioValue.Стратегия не найдена в strategyCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C838562() {
        strategyId = UUID.randomUUID();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        Optional<MasterPortfolioValue> portfolioValue = masterPortfolioValueDao.findMasterPortfolioValueByStrategyId(strategyId);
        assertThat("запись по расчету стоимости портфеля не равно", portfolioValue.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("838564")
    @DisplayName("C838564.CalculateMasterPortfolioValue.Не найден виртуальный портфель" +
        " в materialized view changed_at_master_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C838564() {
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
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        Optional<MasterPortfolioValue> portfolioValue = masterPortfolioValueDao.findMasterPortfolioValueByStrategyId(strategyId);
        assertThat("запись по расчету стоимости портфеля не равно", portfolioValue.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("848110")
    @DisplayName("C848110.CalculateMasterPortfolioValue.Найдена запись в таблице master_portfolio_value " +
        "по ключу: strategy_id, cut, если operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C848110() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        String baseMoney = "119335.55";
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
        // оздаем записи по портфелям
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster, strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster, strategyId);
        steps.createMasterPortfolioTwoPosition(20, 3, "119335.55", contractIdMaster, strategyId);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);
        // формируем список позиций для запроса prices MD
        String ListInst = steps.instrumet1 + "," + steps.instrumet2;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(steps.ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(steps.classCode2)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster, strategyId);
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
//        Thread.sleep(5000);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
    }


    @SneakyThrows
    @Test
    @AllureId("986488")
    @DisplayName("C986488.CalculateMasterPortfolioValue.Найдена запись в таблице master_portfolio_value " +
        "по ключу: strategy_id, cut, если operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C986488() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        String baseMoney = "119335.55";
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
        // оздаем записи по портфелям
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster, strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster, strategyId);
        steps.createMasterPortfolioTwoPosition(20, 3, "119335.55", contractIdMaster, strategyId);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(cutTime);
        // формируем список позиций для запроса prices MD
        String ListInst = steps.instrumet1 + "," + steps.instrumet2;
        //вызываем метод MD и сохраняем prices в Map
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(steps.ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(steps.classCode2)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(TEN_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
        String baseMoneyNew = "77545.55";
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster, strategyId);
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        BigDecimal pricePos3 = new BigDecimal(steps.getPriceFromMarketDataWithDate(steps.instrumet3, "last", dateTs));
        //выполняем расчеты
        BigDecimal valuePos3 = new BigDecimal(steps.quantity3).multiply(pricePos3);
        BigDecimal valuePortfolioNew = valuePos1.add(valuePos2).add(valuePos3).add(new BigDecimal(baseMoneyNew));
        ;
        log.info("valuePortfolioNew:{}", valuePortfolioNew);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolioNew));
    }


    @SneakyThrows
    @Test
    @AllureId("884062")
    @DisplayName("C884062.CalculateMasterPortfolioValue.Расчет стоимости виртуального портфеля," +
        " если operation = 'CALCULATE' позиция не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C884062() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String ticker1 = "TEST";
        String tradingClearingAccount1 = "L01+00000F00";
        String quantity1 = "50";
        String baseMoney = "16551.10";
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
        createMasterPortfolio(ticker1, tradingClearingAccount1, quantity1, steps.ticker8, steps.tradingClearingAccount8, steps.quantity8);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime);
        BigDecimal pricePos2 = new BigDecimal(steps.getPriceFromMarketDataWithDate(steps.instrumet8, "last", dateTs));
        //выполняем расчеты
        BigDecimal valuePos2 = new BigDecimal(steps.quantity8).multiply(pricePos2);
        BigDecimal valuePortfolio = valuePos2.add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    @SneakyThrows
    @Test
    @AllureId("884361")
    @DisplayName("C884361.CalculateMasterPortfolioValue.Расчет стоимости виртуального портфеля, если operation = 'CALCULATE'," +
        " для позиции цена не была найдена в instrumentPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C884361() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String ticker1 = "FXITTEST";
        String tradingClearingAccount1 = "L01+00002F00";
        String quantity1 = "500";
        String baseMoney = "16551.10";
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
        createMasterPortfolio(ticker1, tradingClearingAccount1, quantity1, steps.ticker8, steps.tradingClearingAccount8, steps.quantity8);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_VALUE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(cutTime);
        BigDecimal pricePos2 = new BigDecimal(steps.getPriceFromMarketDataWithDate(steps.instrumet8, "last", dateTs));
        //выполняем расчеты
        BigDecimal valuePos2 = new BigDecimal(steps.quantity8).multiply(pricePos2);
        BigDecimal valuePortfolio = valuePos2.add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioValue = masterPortfolioValueDao.getMasterPortfolioValueByStrategyId(strategyId), notNullValue());
        assertThat("value стоимости портфеля не равно", masterPortfolioValue.getValue(), is(valuePortfolio));
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioValue.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }


    //методы для работы тестов**********************************************************************

    List getPosListOne(String ticker, String tradingClearingAccount, String quantity,
                       Tracking.Portfolio.Position positionAction, Date date) {
        List<MasterPortfolio.Position> positionListMasterOne = new ArrayList<>();
        positionListMasterOne.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantity))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionListMasterOne;
    }

    List getPosListTwo(String ticker1, String tradingClearingAccount1, String quantity1,
                       String ticker2, String tradingClearingAccount2, String quantity2,
                       Tracking.Portfolio.Position positionAction, Date date) {
        List<MasterPortfolio.Position> positionListMasterOne = new ArrayList<>();
        positionListMasterOne.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMasterOne.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionListMasterOne;
    }


    void createMasterPortfolio(String ticker1, String tradingClearingAccount1, String quantity1,
                               String ticker2, String tradingClearingAccount2, String quantity2) {
        steps.createMasterPortfolioWithOutPosition(15, 1, "47390.90", contractIdMaster, strategyId);
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).minusHours(3);
        Date dateOne = Date.from(utc.toInstant());
        OffsetDateTime utcTwo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).minusHours(2);
        Date dateTwo = Date.from(utcTwo.toInstant());
        List<MasterPortfolio.Position> positionListMasterOne = getPosListOne(ticker1, tradingClearingAccount1, quantity1, positionAction, dateOne);
        steps.createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMasterOne, 2, "32560.90", dateOne);
        List<MasterPortfolio.Position> positionListMasterTwo = getPosListTwo(ticker1, tradingClearingAccount1, quantity1,
            ticker2, tradingClearingAccount2, quantity2, positionAction, dateOne);
        steps.createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMasterTwo, 3, "16551.10", dateTwo);
    }


    void createMasterPortfolios() {
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster, strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster, strategyId);
        steps.createMasterPortfolioTwoPosition(20, 3, "119335.55", contractIdMaster, strategyId);
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster, strategyId);
        steps.createMasterPortfolioFourPosition(10, 5, "73445.55", contractIdMaster, strategyId);
        steps.createMasterPortfolioFivePosition(8, 6, "57545.35", contractIdMaster, strategyId);
        steps.createMasterPortfolioSixPosition(6, 7, "34545.78", contractIdMaster, strategyId);
        steps.createMasterPortfolioSevenPosition(3, 8, "16551.10", contractIdMaster, strategyId);
    }

}