package stpTrackingAnalytics.calculateMasterPortfolioRate;

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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioRate;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.PositionDateFromFireg;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.steps.StpTrackingAnalyticsSteps;
import ru.tinkoff.trading.tracking.Tracking;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


@Slf4j
@Epic("calculateMasterPortfolioRate Расчет долей виртуального портфеля")
@Feature("TAP-9584")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class

})
public class CalculateMasterPortfolioRateTest {
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
    MasterPortfolioRateDao masterPortfolioRateDao;
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
    Client client;
    Contract contract;
    UUID strategyId;
    MasterPortfolioRate masterPortfolioRate;
    String SIEBEL_ID_MASTER = "5-AJ7L9FNI";
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    String contractIdMaster;

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }

            try {
                masterPortfolioRateDao.deleteMasterPortfolioRateByStrategyId(strategyId);
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
    @AllureId("966227")
    @DisplayName("C966227.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C966227(Tracking.AnalyticsCommand.Operation operation) {
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolios();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE, strategyIdByte);
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
        String ListInst = steps.instrumet1 + "," +  steps.instrumet2 + "," + steps.instrumet3 + "," +  steps.instrumet4 + "," +
            steps.instrumet5 + "," +  steps.instrumet6 + "," +  steps.instrumet7;
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 7);
        //получаем параметры для расчета стоимости портфеля bonds
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
        //группируем данные по показателям
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = getPositionsMap(pricesPos,  nominal,
             minPriceIncrement, aciValue,  baseMoney);
        BigDecimal valuePortfolio = getValuePortfolio(pricesPos,  nominal,
        minPriceIncrement, aciValue,  baseMoney);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        //определяем стоимость каждой группы
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
    }



    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("978635")
    @DisplayName("C978635.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля, на заданную метку времени среза")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C978635(Tracking.AnalyticsCommand.Operation operation) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String baseMoney = "77545.55";
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolios();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now().minusDays(12);
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            operation, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE, strategyIdByte);
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
        String ListInst = steps.instrumet1 + "," +  steps.instrumet2 + "," + steps.instrumet3;
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 3);
        //получаем параметры для расчета стоимости портфеля bonds
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
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = valuePosBonds(priceTs,nominal,minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(steps.instrumet3)) {
                valuePos3 = new BigDecimal(steps.quantity3).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(valuePos3)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker2, steps.tradingClearingAccount2, steps.type2, steps.sector2, steps.company2), valuePos2);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker3, steps.tradingClearingAccount3, steps.type3, steps.sector3, steps.company3), valuePos3);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
    }



    @SneakyThrows
    @Test
    @AllureId("978760")
    @DisplayName("C978760.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля," +
        " найдена запись на метку времени в master_portfolio_rate, operation = 'CALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на" +
        " заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C978760() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String baseMoney = "119335.55";
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster, strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster, strategyId);
        steps.createMasterPortfolioTwoPosition(20,3,"119335.55",contractIdMaster, strategyId);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE,
            strategyIdByte);
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
        String ListInst = steps.instrumet1 + "," +  steps.instrumet2;
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        //получаем значения по bond из fireg
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
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = valuePosBonds(priceTs,nominal,minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker2, steps.tradingClearingAccount2, steps.type2, steps.sector2, steps.company2), valuePos2);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster,strategyId);
        steps.createMasterPortfolioFourPosition(10, 5, "73445.55", contractIdMaster,strategyId);
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Thread.sleep(5000);
        masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
    }


    @SneakyThrows
    @Test
    @AllureId("978790")
    @DisplayName("C978790.Расчет долей виртуального портфеля, найдена запись на метку времени в master_portfolio_rate," +
        "operation = 'RECALCULATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля" +
        " на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C978790() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String baseMoney = "119335.55";
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster,strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster,strategyId);
        steps.createMasterPortfolioTwoPosition(20,3,"119335.55", contractIdMaster,strategyId );
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.RECALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE,
            strategyIdByte);
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
        String ListInst = steps.instrumet1 + "," +  steps.instrumet2;
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        //получаем значения по bond из fireg
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
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = valuePosBonds(priceTs,nominal,minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker2, steps.tradingClearingAccount2, steps.type2, steps.sector2, steps.company2), valuePos2);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
        //добавляем версия портфеля мастера
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster, strategyId);
        steps.createMasterPortfolioFourPosition(10, 5, "73445.55", contractIdMaster, strategyId);
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //расчитываем новые доли
        String baseMoneyNew = "73445.55";
        String ListInstNew =   steps.instrumet1 + "," +  steps.instrumet2 + "," +  steps.instrumet3 + "," +  steps.instrumet4;
        Map<String, BigDecimal> pricesPosNew = steps.getPriceFromMarketAllDataWithDate(ListInstNew, "last", dateTs, 4);
        BigDecimal valuePosNew1 = BigDecimal.ZERO;
        BigDecimal valuePosNew2 = BigDecimal.ZERO;
        BigDecimal valuePosNew3 = BigDecimal.ZERO;
        BigDecimal valuePosNew4 = BigDecimal.ZERO;
        Iterator itNew = pricesPosNew.entrySet().iterator();
        while (itNew.hasNext()) {
            Map.Entry pair = (Map.Entry)itNew.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePosNew1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePosNew2 = valuePosBonds(priceTs,nominal,minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(steps.instrumet3)) {
                valuePosNew3 = new BigDecimal(steps.quantity3).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet4)) {
                valuePosNew4 = new BigDecimal(steps.quantity4).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolioNew = valuePosNew1
            .add(valuePosNew2)
            .add(valuePosNew3)
            .add(valuePosNew4)
            .add(new BigDecimal(baseMoneyNew));
        log.info("valuePortfolioNew:  {}", valuePortfolioNew);
        Map<PositionDateFromFireg, BigDecimal> positionIdMapNew = new HashMap<>();
        positionIdMapNew.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePosNew1);
        positionIdMapNew.put(new PositionDateFromFireg(steps.ticker2, steps.tradingClearingAccount2, steps.type2, steps.sector2, steps.company2), valuePosNew2);
        positionIdMapNew.put(new PositionDateFromFireg(steps.ticker3, steps.tradingClearingAccount3, steps.type3, steps.sector3, steps.company3), valuePosNew3);
        positionIdMapNew.put(new PositionDateFromFireg(steps.ticker4, steps.tradingClearingAccount4, steps.type4, steps.sector4, steps.company4), valuePosNew4);
        Map<String, BigDecimal> sectorsNew = getSectors(positionIdMapNew, baseMoneyNew);
        Map<String, BigDecimal> typesNew = getTypes(positionIdMapNew, baseMoneyNew);
        Map<String, BigDecimal> companysNew = getCompanys(positionIdMapNew, baseMoneyNew);
        sectorsNew.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolioNew, 4, RoundingMode.HALF_UP));
        typesNew.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolioNew, 4, RoundingMode.HALF_UP));
        companysNew.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolioNew, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        //Проверяем параметры
        checkParam ( sectorsNew, typesNew, companysNew,   cut, cutInCommand);
    }




    @SneakyThrows
    @Test
    @AllureId("966228")
    @DisplayName("C966228.CalculateMasterPortfolioRat.Стратегия не найдена в strategyCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля " +
        "на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C966228() {
        strategyId = UUID.randomUUID();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Optional<MasterPortfolioRate> portfolioRate = masterPortfolioRateDao.findMasterPortfolioRateByStrategyId(strategyId);
        assertThat("запись по расчету долей портфеля не равно", portfolioRate.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("978840")
    @DisplayName("978840.CalculateMasterPortfolioRate.Не найден виртуальный портфель в materialized view changed_at_master_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля " +
        "на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C978840() {
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE,
            strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        Optional<MasterPortfolioValue> portfolioValue = masterPortfolioValueDao.findMasterPortfolioValueByStrategyId(strategyId);
        assertThat("запись по расчету стоимости портфеля не равно", portfolioValue.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("978841")
    @DisplayName("C978841.CalculateMasterPortfolioRate.Позиция не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C978841() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String ticker8 = "TEST";
        String tradingClearingAccount8 = "L01+00000F00";
        String quantity8 = "3";
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        createMasterPortfolio(steps.ticker1, steps.tradingClearingAccount1, steps.quantity1, ticker8, tradingClearingAccount8, quantity8);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE,
            strategyIdByte);
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
        String ListInst = steps.instrumet1;
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 1);
        BigDecimal valuePos1 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePos1);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
    }


    @SneakyThrows
    @Test
    @AllureId("978843")
    @DisplayName("C978843.CalculateMasterPortfolioRate.Не найдена цена для позиции цена в instrumentPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C978843() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        String ticker8 = "FXITTEST";
        String tradingClearingAccount8 = "L01+00002F00";
        String quantity8 = "3";
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
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        createMasterPortfolio(steps.ticker1, steps.tradingClearingAccount1, steps.quantity1, ticker8, tradingClearingAccount8, quantity8);
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду CALCULATE
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE,
            strategyIdByte);
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
        String ListInst = steps.instrumet1;
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 1);
        BigDecimal valuePos1 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePos1);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam ( sectors, types, companys,   cut, cutInCommand);
    }



    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategy = trackingService.saveStrategy(strategy);
    }


    Map<PositionDateFromFireg, BigDecimal> getPositionsMap(Map<String, BigDecimal> pricesPos, String nominal,
                                                           BigDecimal minPriceIncrement, String aciValue,String baseMoney) {
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal valuePos4 = BigDecimal.ZERO;
        BigDecimal valuePos5 = BigDecimal.ZERO;
        BigDecimal valuePos6 = BigDecimal.ZERO;
        BigDecimal valuePos7 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal minPriceIncrementNew = minPriceIncrement
                    .multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
                    .multiply(minPriceIncrementNew);
                BigDecimal price =roundPrice
                    .add(new BigDecimal(aciValue));
                valuePos2 = new BigDecimal(steps.quantity2).multiply(price);
            }
            if (pair.getKey().equals(steps.instrumet3)) {
                valuePos3 = new BigDecimal(steps.quantity3).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet4)) {
                valuePos4 = new BigDecimal(steps.quantity4).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet5)) {
                valuePos5 = new BigDecimal(steps.quantity5).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet6)) {
                valuePos6 = new BigDecimal(steps.quantity6).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet7)) {
                valuePos7 = new BigDecimal(steps.quantity7).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(valuePos3)
            .add(valuePos4)
            .add(valuePos5)
            .add(valuePos6)
            .add(valuePos7)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(steps.ticker1, steps.tradingClearingAccount1, steps.type1, steps.sector1, steps.company1), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker2, steps.tradingClearingAccount2, steps.type2, steps.sector2, steps.company2), valuePos2);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker3, steps.tradingClearingAccount3, steps.type3, steps.sector3, steps.company3), valuePos3);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker4, steps.tradingClearingAccount4, steps.type4, steps.sector4, steps.company4), valuePos4);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker5, steps.tradingClearingAccount5, steps.type5, steps.sector5, steps.company5), valuePos5);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker6, steps.tradingClearingAccount6, steps.type6, steps.sector6, steps.company6), valuePos6);
        positionIdMap.put(new PositionDateFromFireg(steps.ticker7, steps.tradingClearingAccount7, steps.type7, steps.sector7, steps.company7), valuePos7);
        return positionIdMap;
    }

   BigDecimal getValuePortfolio(Map<String, BigDecimal> pricesPos, String nominal,
                                                           BigDecimal minPriceIncrement, String aciValue,String baseMoney) {
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal valuePos4 = BigDecimal.ZERO;
        BigDecimal valuePos5 = BigDecimal.ZERO;
        BigDecimal valuePos6 = BigDecimal.ZERO;
        BigDecimal valuePos7 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(steps.instrumet1)) {
                valuePos1 = new BigDecimal(steps.quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet2)) {
                String priceTs = pair.getValue().toString();
                BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal minPriceIncrementNew = minPriceIncrement
                    .multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
                    .multiply(minPriceIncrementNew);
                BigDecimal price =roundPrice
                    .add(new BigDecimal(aciValue));
                valuePos2 = new BigDecimal(steps.quantity2).multiply(price);
            }
            if (pair.getKey().equals(steps.instrumet3)) {
                valuePos3 = new BigDecimal(steps.quantity3).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet4)) {
                valuePos4 = new BigDecimal(steps.quantity4).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet5)) {
                valuePos5 = new BigDecimal(steps.quantity5).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet6)) {
                valuePos6 = new BigDecimal(steps.quantity6).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(steps.instrumet7)) {
                valuePos7 = new BigDecimal(steps.quantity7).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(valuePos3)
            .add(valuePos4)
            .add(valuePos5)
            .add(valuePos6)
            .add(valuePos7)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);

        return valuePortfolio;
    }

    Map<String, BigDecimal> getSectors (Map<PositionDateFromFireg, BigDecimal> positionIdMap, String baseMoney){
        Map<String, BigDecimal> sectors = positionIdMap.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getSector(),
                Map.Entry::getValue, BigDecimal::add));
        sectors.merge("money", new BigDecimal(baseMoney), BigDecimal::add);
        return sectors;
    }

    Map<String, BigDecimal> getTypes (Map<PositionDateFromFireg, BigDecimal> positionIdMap, String baseMoney){
        Map<String, BigDecimal> types = positionIdMap.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getType(),
                Map.Entry::getValue, BigDecimal::add));
        types.merge("money", new BigDecimal(baseMoney), BigDecimal::add);
        return types;
    }

    Map<String, BigDecimal> getCompanys (Map<PositionDateFromFireg, BigDecimal> positionIdMap, String baseMoney){
        Map<String, BigDecimal> companys = positionIdMap.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getCompany(),
                Map.Entry::getValue, BigDecimal::add));
        companys.merge("Денежные средства", new BigDecimal(baseMoney), BigDecimal::add);
        return companys;
    }


    void checkParam (Map<String, BigDecimal> sectors, Map<String, BigDecimal> types,Map<String,
        BigDecimal> companys,  LocalDateTime cut, LocalDateTime cutInCommand) {
        assertThat("доли по секторам не равны", true, is(sectors.equals(masterPortfolioRate.getSectorToRateMap())));
        assertThat("доли типам  не равны", true, is(types.equals(masterPortfolioRate.getTypeToRateMap())));
        assertThat("доли по компаниям не равны", true, is(companys.equals(masterPortfolioRate.getCompanyToRateMap())));
        assertThat("время cut не равно", true, is(cut.equals(cutInCommand)));
    }

    BigDecimal valuePosBonds(String priceTs, String nominal,BigDecimal minPriceIncrement, String aciValue, BigDecimal valuePos) {
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price =roundPrice
            .add(new BigDecimal(aciValue));
        valuePos = new BigDecimal(steps.quantity2).multiply(price);
        return valuePos;
    }


    void createMasterPortfolios() {
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10",contractIdMaster,  strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster,  strategyId);
        steps.createMasterPortfolioTwoPosition(20,3,"119335.55", contractIdMaster,  strategyId);
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster,  strategyId);
        steps.createMasterPortfolioFourPosition(10, 5, "73445.55", contractIdMaster,  strategyId);
        steps.createMasterPortfolioFivePosition(8, 6, "57545.35", contractIdMaster,  strategyId);
        steps.createMasterPortfolioSixPosition(6, 7, "34545.78", contractIdMaster,  strategyId);
        steps.createMasterPortfolioSevenPosition(3, 8, "16551.10", contractIdMaster,  strategyId);
    }

    List getPosListOne(String ticker, String tradingClearingAccount, String quantity,
                       Tracking.Portfolio.Position positionAction,Date date) {
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
                       Tracking.Portfolio.Position positionAction,Date date) {
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
        List<MasterPortfolio.Position> positionListMasterOne =getPosListOne(ticker1, tradingClearingAccount1, quantity1, positionAction, dateOne);
        steps.createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMasterOne, 2, "32560.90", dateOne);
        List<MasterPortfolio.Position> positionListMasterTwo =getPosListTwo(ticker1, tradingClearingAccount1, quantity1,
            ticker2, tradingClearingAccount2,  quantity2,positionAction, dateOne);
        steps.createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMasterTwo, 3, "16551.10", dateTwo);
    }
}
