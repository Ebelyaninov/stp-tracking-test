package stpTrackingAnalytics.calculateMasterPortfolioRate;

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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioRate;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.PositionDateFromFireg;
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
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;


@Slf4j
@Epic("calculateMasterPortfolioRate Расчет долей виртуального портфеля")
@Feature("TAP-9584")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-analytics")
@Tags({@Tag("stp-tracking-analytics"), @Tag("calculateMasterPortfolioRate")})
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
    MasterPortfolioRateDao masterPortfolioRateDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    StpTrackingAnalyticsSteps steps;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;

    UUID strategyId;
    MasterPortfolioRate masterPortfolioRate;

    String contractIdMaster;
    UUID investIdMaster;

    String description = "new test стратегия autotest";


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
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }

            try {
                masterPortfolioRateDao.deleteMasterPortfolioRateByStrategyId(strategyId);
            } catch (Exception e) {
            }
        });
    }

    @BeforeAll
    void getDataClients() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdMasterAnalytics1);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(contractIdMaster, investIdMaster);
    }

    private static Stream<Arguments> provideAnalyticsCommand() {
        return Stream.of(
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE, StrategyStatus.active, LocalDateTime.now()),
            Arguments.of(Tracking.AnalyticsCommand.Operation.RECALCULATE, StrategyStatus.active, LocalDateTime.now()),
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE, StrategyStatus.frozen, LocalDateTime.now()),
            Arguments.of(Tracking.AnalyticsCommand.Operation.RECALCULATE, StrategyStatus.frozen, LocalDateTime.now())
        );
    }


    private static Stream<Arguments> provideStrategyStatus() {
        return Stream.of(
            Arguments.of(Tracking.AnalyticsCommand.Operation.CALCULATE, StrategyStatus.draft, null, null),
            Arguments.of(Tracking.AnalyticsCommand.Operation.RECALCULATE, StrategyStatus.closed, LocalDateTime.now().minusDays(1), LocalDateTime.now())
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("966227")
    @DisplayName("C966227.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C966227(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status, LocalDateTime time) {
        strategyId = UUID.randomUUID();
        String baseMoney = "16551.10";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, time, null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolios();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        OffsetDateTime cutTimeForMd = cutTime.minusHours(3);
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
        String dateTs = fmt.format(cutTimeForMd);
        String dateFireg = fmtFireg.format(cutTime);
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        instrumentList.add(instrument.instrumentLKOH);
        instrumentList.add(instrument.instrumentSNGSP);
        instrumentList.add(instrument.instrumentTRNFP);
        instrumentList.add(instrument.instrumentESGR);
        instrumentList.add(instrument.instrumentUSD);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 7);
        //получаем параметры для расчета стоимости портфеля bonds
        List<String> getBondDate = steps.getDateBondFromInstrument(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getBondDate.get(0);
        String nominal = getBondDate.get(1);
        //группируем данные по показателям
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = getPositionsMap(pricesPos, nominal,
            minPriceIncrement, aciValue, baseMoney);
        BigDecimal valuePortfolio = getValuePortfolio(pricesPos, nominal,
            minPriceIncrement, aciValue, baseMoney);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        //определяем стоимость каждой группы
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_DOWN));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_DOWN));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_DOWN));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        checkMasterPortfolioRate(strategyId);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("978635")
    @DisplayName("C978635.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля, на заданную метку времени среза")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C978635(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status, LocalDateTime time) {
        strategyId = UUID.randomUUID();
        String baseMoney = "77545.55";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            status, 0, time, null);
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
        String dateTs = fmt.format(cutTime.minusHours(3));
        String dateFireg = fmtFireg.format(cutTime);
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        instrumentList.add(instrument.instrumentLKOH);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 3);
        //получаем параметры для расчета стоимости портфеля bonds
        List<String> getBondDate = steps.getDateBondFromInstrument(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getBondDate.get(0);
        String nominal = getBondDate.get(1);
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
                valuePos2 = valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(valuePos3)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, instrument.typeSU29009RMFS6, instrument.sectorSU29009RMFS6, instrument.companySU29009RMFS6), valuePos2);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerLKOH, instrument.tradingClearingAccountLKOH, instrument.typeLKOH, instrument.sectorLKOH, instrument.companyLKOH), valuePos3);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        checkMasterPortfolioRate(strategyId);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
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
        strategyId = UUID.randomUUID();
        String baseMoney = "119335.55";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        // создаем портфель ведущего с позициями в кассандре
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster, strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster, strategyId);
        steps.createMasterPortfolioTwoPosition(20, 3, "119335.55", contractIdMaster, strategyId);
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
        String dateTs = fmt.format(cutTime.minusHours(3));
        String dateFireg = fmtFireg.format(cutTime);
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 2);
        //получаем параметры для расчета стоимости портфеля bonds
        List<String> getBondDate = steps.getDateBondFromInstrument(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getBondDate.get(0);
        String nominal = getBondDate.get(1);
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
                valuePos2 = valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, instrument.typeSU29009RMFS6, instrument.sectorSU29009RMFS6, instrument.companySU29009RMFS6), valuePos2);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        checkMasterPortfolioRate(strategyId);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster, strategyId);
        steps.createMasterPortfolioFourPosition(10, 5, "73445.55", contractIdMaster, strategyId);
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
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
        strategyId = UUID.randomUUID();
        String baseMoney = "119335.55";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        // создаем портфель ведущего с позициями в кассандре
        steps.createMasterPortfolioWithOutPosition(31, 1, "136551.10", contractIdMaster, strategyId);
        steps.createMasterPortfolioOnePosition(25, 2, "122551.1", contractIdMaster, strategyId);
        steps.createMasterPortfolioTwoPosition(20, 3, "119335.55", contractIdMaster, strategyId);
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
        String dateTs = fmt.format(cutTime.minusHours(3));
        String dateFireg = fmtFireg.format(cutTime);
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 2);
        //получаем параметры для расчета стоимости портфеля bonds
        List<String> getBondDate = steps.getDateBondFromInstrument(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getBondDate.get(0);
        String nominal = getBondDate.get(1);
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
                valuePos2 = valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(valuePos2)
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, instrument.typeSU29009RMFS6, instrument.sectorSU29009RMFS6, instrument.companySU29009RMFS6), valuePos2);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        await().pollDelay(Duration.ofMillis(500));
        checkMasterPortfolioRate(strategyId);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
        //добавляем версия портфеля мастера
        steps.createMasterPortfolioThreePosition(15, 4, "77545.55", contractIdMaster, strategyId);
        steps.createMasterPortfolioFourPosition(10, 5, "73445.55", contractIdMaster, strategyId);
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);
        //расчитываем новые доли
        String baseMoneyNew = "73445.55";
        String ListInstNew = instrument.instrumentSBER + "," + instrument.instrumentSU29009RMFS6 + "," + instrument.instrumentLKOH + "," + instrument.instrumentSNGSP;
        instrumentList.add(instrument.instrumentLKOH);
        instrumentList.add(instrument.instrumentSNGSP);
        Map<String, BigDecimal> pricesPosNew = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 4);
        BigDecimal valuePosNew1 = BigDecimal.ZERO;
        BigDecimal valuePosNew2 = BigDecimal.ZERO;
        BigDecimal valuePosNew3 = BigDecimal.ZERO;
        BigDecimal valuePosNew4 = BigDecimal.ZERO;
        Iterator itNew = pricesPosNew.entrySet().iterator();
        while (itNew.hasNext()) {
            Map.Entry pair = (Map.Entry) itNew.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePosNew1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePosNew2 = valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, valuePos2);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePosNew3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSNGSP)) {
                valuePosNew4 = new BigDecimal(steps.quantitySNGSP).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolioNew = valuePosNew1
            .add(valuePosNew2)
            .add(valuePosNew3)
            .add(valuePosNew4)
            .add(new BigDecimal(baseMoneyNew));
        log.info("valuePortfolioNew:  {}", valuePortfolioNew);
        Map<PositionDateFromFireg, BigDecimal> positionIdMapNew = new HashMap<>();
        positionIdMapNew.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePosNew1);
        positionIdMapNew.put(new PositionDateFromFireg(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, instrument.typeSU29009RMFS6, instrument.sectorSU29009RMFS6, instrument.companySU29009RMFS6), valuePosNew2);
        positionIdMapNew.put(new PositionDateFromFireg(instrument.tickerLKOH, instrument.tradingClearingAccountLKOH, instrument.typeLKOH, instrument.sectorLKOH, instrument.companyLKOH), valuePosNew3);
        positionIdMapNew.put(new PositionDateFromFireg(instrument.tickerSNGSP, instrument.tradingClearingAccountSNGSP, instrument.typeSNGSP, instrument.sectorSNGSP, instrument.companySNGSP), valuePosNew4);
        Map<String, BigDecimal> sectorsNew = getSectors(positionIdMapNew, baseMoneyNew);
        Map<String, BigDecimal> typesNew = getTypes(positionIdMapNew, baseMoneyNew);
        Map<String, BigDecimal> companysNew = getCompanys(positionIdMapNew, baseMoneyNew);
        sectorsNew.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolioNew, 4, RoundingMode.HALF_UP));
        typesNew.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolioNew, 4, RoundingMode.HALF_UP));
        companysNew.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolioNew, 4, RoundingMode.HALF_UP));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSumNew = calculateSumWithoutMoneyGroup(sectorsNew, "money");
        BigDecimal sectorRateSumNew = calculateSumWithoutMoneyGroup(typesNew, "money");
        BigDecimal companyRateSumNew = calculateSumWithoutMoneyGroup(companysNew, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectorsNew.put("money", BigDecimal.ONE.subtract(typeRateSumNew));
        typesNew.put("money", BigDecimal.ONE.subtract(sectorRateSumNew));
        companysNew.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSumNew));
        checkMasterPortfolioRate(strategyId);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        //Проверяем параметры
        checkParam(sectorsNew, typesNew, companysNew, cut, cutInCommand);
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
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
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
        strategyId = UUID.randomUUID();
        String ticker8 = "TEST";
        String tradingClearingAccount8 = "L01+00000F00";
        String quantity8 = "3";
        String baseMoney = "16551.10";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        createMasterPortfolio(instrument.tickerSBER, instrument.tradingClearingAccountSBER, steps.quantitySBER, ticker8, tradingClearingAccount8, quantity8);
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
        String dateTs = fmt.format(cutTime.minusHours(3));
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 1);
        BigDecimal valuePos1 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePos1);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
    }


    @SneakyThrows
    @Test
    @AllureId("978843")
    @DisplayName("C978843.CalculateMasterPortfolioRate.Не найдена цена для позиции цена в instrumentPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и пересчитывает стоимость виртуального портфеля на заданную метку времени.")
    void C978843() {
        strategyId = UUID.randomUUID();
        String ticker8 = "FXITTEST";
        String tradingClearingAccount8 = "L01+00002F00";
        String quantity8 = "3";
        String baseMoney = "16551.10";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        createMasterPortfolio(instrument.tickerSBER, instrument.tradingClearingAccountSBER, steps.quantitySBER, ticker8, tradingClearingAccount8, quantity8);
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
        String dateTs = fmt.format(cutTime.minusHours(3));
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSBER);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 1);
        BigDecimal valuePos1 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos1
            .add(new BigDecimal(baseMoney));
        log.info("valuePortfolio:  {}", valuePortfolio);
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePos1);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_UP));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
    }


    @SneakyThrows
    @Test
    @AllureId("966227")
    @DisplayName("C966227.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C966227___111() {
        strategyId = UUID.randomUUID();
        String tickerShare = "SBER";
        String tradingClearingAccountShare = "NDS000000001";
        String quantityShare = "30";

        String tickerEtf = "FXDE";
        String tradingClearingAccountEtf = "NDS000000001";
        String quantityEtf = "5";

        String tickerBond = "SU29009RMFS6";
        String tradingClearingAccountBond = "NDS000000001";
        String quantityBond = "7";

        String tickerMoney = "USD000UTSTOM";
        String tradingClearingAccountMoney = "MB9885503216";
        String quantityMoney = "2000";
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        String baseMoney = "16551.10";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        // создаем портфель ведущего с позициями в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal(quantityShare))
            .changedAt(date)
            .lastChangeDetectedVersion(7)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerEtf)
            .tradingClearingAccount(tradingClearingAccountEtf)
            .quantity(new BigDecimal(quantityEtf))
            .changedAt(date)
            .lastChangeDetectedVersion(7)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal(quantityBond))
            .changedAt(date)
            .lastChangeDetectedVersion(7)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerMoney)
            .tradingClearingAccount(tradingClearingAccountMoney)
            .quantity(new BigDecimal(quantityMoney))
            .changedAt(date)
            .lastChangeDetectedVersion(7)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());

        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal("6259.17"))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, 7, baseMoneyPosition, date, positionList);

        createMasterPortfolios();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем команду
        Tracking.AnalyticsCommand calculateCommand = steps.createCommandAnalytics(createTime, cutTime,
            Tracking.AnalyticsCommand.Operation.CALCULATE, Tracking.AnalyticsCommand.Calculation.MASTER_PORTFOLIO_RATE, strategyIdByte);
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = calculateCommand.toByteArray();
        byte[] keyBytes = strategyIdByte.toByteArray();
        //отправляем событие в топик kafka tracking.analytics.command
        byteToByteSenderService.send(Topics.TRACKING_ANALYTICS_COMMAND, keyBytes, eventBytes);

        //Проверяем параметры
        log.info("Команда в tracking.analytics.command:  {}", calculateCommand);
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAnalyticsCommand")
    @AllureId("1781965")
    @DisplayName("C1781965.CalculateMasterPortfolioRat.Расчет долей виртуального портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C1781965(Tracking.AnalyticsCommand.Operation operation) {
        strategyId = UUID.randomUUID();
        String baseMoney = "16551.10";
        BigDecimal minPriceIncrement = new BigDecimal("0.001");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfoliosWithZero();
        ByteString strategyIdByte = steps.byteString(strategyId);
        OffsetDateTime createTime = OffsetDateTime.now();
        OffsetDateTime cutTime = OffsetDateTime.now();
        OffsetDateTime cutTimeForMd = cutTime.minusHours(3);
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
        String dateTs = fmt.format(cutTimeForMd);
        String dateFireg = fmtFireg.format(cutTime);
        List<String> instrumentList = new ArrayList<>();
        instrumentList.add(instrument.instrumentSU29009RMFS6);
        instrumentList.add(instrument.instrumentLKOH);
        instrumentList.add(instrument.instrumentSNGSP);
        instrumentList.add(instrument.instrumentTRNFP);
        instrumentList.add(instrument.instrumentUSD);
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(instrumentList, "last", dateTs, 5);
        //получаем параметры для расчета стоимости портфеля bonds
        List<String> getBondDate = steps.getDateBondFromInstrument(instrument.tickerSU29009RMFS6, instrument.classCodeSU29009RMFS6, dateFireg);
        String aciValue = getBondDate.get(0);
        String nominal = getBondDate.get(1);
        //группируем данные по показателям
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = getPositionsMapWithZero(pricesPos, nominal,
            minPriceIncrement, aciValue, baseMoney);
        BigDecimal valuePortfolio = steps.getValuePortfolioWithZero(pricesPos, nominal,
            minPriceIncrement, aciValue, baseMoney);
        Map<String, BigDecimal> sectors = getSectors(positionIdMap, baseMoney);
        Map<String, BigDecimal> types = getTypes(positionIdMap, baseMoney);
        Map<String, BigDecimal> companys = getCompanys(positionIdMap, baseMoney);
        //определяем стоимость каждой группы
        sectors.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_DOWN));
        types.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_DOWN));
        companys.replaceAll((s, BigDecimal) -> BigDecimal.divide(valuePortfolio, 4, RoundingMode.HALF_DOWN));
        //исключаем группу позиций с деньгами
        BigDecimal typeRateSum = calculateSumWithoutMoneyGroup(sectors, "money");
        BigDecimal sectorRateSum = calculateSumWithoutMoneyGroup(types, "money");
        BigDecimal companyRateSum = calculateSumWithoutMoneyGroup(companys, "Денежные средства");
        //обрабатываем оставшуюся группу с денежными позициями
        sectors.put("money", BigDecimal.ONE.subtract(typeRateSum));
        types.put("money", BigDecimal.ONE.subtract(sectorRateSum));
        companys.put("Денежные средства", BigDecimal.ONE.subtract(companyRateSum));
        //checkMasterPortfolioRate(strategyId);
        await().atMost(FIVE_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId), notNullValue());
        LocalDateTime cut = LocalDateTime.ofInstant(masterPortfolioRate.getCut().toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cutInCommand = LocalDateTime.ofInstant(cutTime.toInstant(),
            ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        //Проверяем параметры
        checkParam(sectors, types, companys, cut, cutInCommand);
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStrategyStatus")
    @AllureId("1886692")
    @DisplayName("1886692 CalculateMasterPortfolioRate.Расчет долей виртуального портфеля. Strategy.status NOT IN (active, frozen)")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и пересчитывает структуру виртуального портфеля на заданную метку времени - его доли в разрезе типов актива, секторов и компаний.")
    void C1886692(Tracking.AnalyticsCommand.Operation operation, StrategyStatus status, LocalDateTime activateTime, LocalDateTime closedTime) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            status, 0, activateTime, closedTime);
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
        //Получаем список записей
        await().pollDelay(Duration.ofMillis(500));
        List<MasterPortfolioRate> masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateList(strategyId);
        //Проверяем что ни одна запись не найдена
        assertThat("запись найдена", masterPortfolioRate.size(), is(0));
    }


//методы для работы тестов*******************************************************************************

    //группируем данные по показателям
    @Step("Считаем стоимость позиции в портфеле и добавляем данные в Map: ")
    Map<PositionDateFromFireg, BigDecimal> getPositionsMap(Map<String, BigDecimal> pricesPos, String nominal,
                                                           BigDecimal minPriceIncrement, String aciValue, String baseMoney) {
        log.info("Считаем стоимость позиции в портфеле");
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal valuePos4 = BigDecimal.ZERO;
        BigDecimal valuePos5 = BigDecimal.ZERO;
        BigDecimal valuePos6 = BigDecimal.ZERO;
        BigDecimal valuePos7 = BigDecimal.ZERO;

        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentSBER + "в портфеле: " + valuePos1);
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal minPriceIncrementNew = minPriceIncrement
                    .multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
                    .multiply(minPriceIncrementNew);
                BigDecimal price = roundPrice
                    .add(new BigDecimal(aciValue));
                valuePos2 = new BigDecimal(steps.quantitySU29009RMFS6).multiply(price);
                log.info("Считаем стоимость позиции " + instrument.instrumentSU29009RMFS6 + "в портфеле: " + valuePos2);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentLKOH + "в портфеле: " + valuePos3);
            }
            if (pair.getKey().equals(instrument.instrumentSNGSP)) {
                valuePos4 = new BigDecimal(steps.quantitySNGSP).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentSNGSP + "в портфеле: " + valuePos4);
            }
            if (pair.getKey().equals(instrument.instrumentTRNFP)) {
                valuePos5 = new BigDecimal(steps.quantityTRNFP).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentTRNFP + "в портфеле: " + valuePos5);
            }
            if (pair.getKey().equals(instrument.instrumentESGR)) {
                valuePos6 = new BigDecimal(steps.quantityESGR).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentESGR + "в портфеле: " + valuePos6);
            }
            if (pair.getKey().equals(instrument.instrumentUSD)) {
                valuePos7 = new BigDecimal(steps.quantityUSD).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentUSD + "в портфеле: " + valuePos7);
            }
        }
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.typeSBER, instrument.sectorSBER, instrument.companySBER), valuePos1);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, instrument.typeSU29009RMFS6, instrument.sectorSU29009RMFS6, instrument.companySU29009RMFS6), valuePos2);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerLKOH, instrument.tradingClearingAccountLKOH, instrument.typeLKOH, instrument.sectorLKOH, instrument.companyLKOH), valuePos3);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSNGSP, instrument.tradingClearingAccountSNGSP, instrument.typeSNGSP, instrument.sectorSNGSP, instrument.companySNGSP), valuePos4);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerTRNFP, instrument.tradingClearingAccountTRNFP, instrument.typeTRNFP, instrument.sectorTRNFP, instrument.companyTRNFP), valuePos5);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerESGR, instrument.tradingClearingAccountESGR, instrument.typeESGR, instrument.sectorESGR, instrument.companyESGR), valuePos6);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerUSD, instrument.tradingClearingAccountUSD, instrument.typeUSD, instrument.sectorUSD, instrument.companyUSD), valuePos7);
        return positionIdMap;
    }


    //группируем данные по показателям
    @Step("Считаем стоимость позиции в портфеле и добавляем данные в Map: ")
    Map<PositionDateFromFireg, BigDecimal> getPositionsMapWithZero(Map<String, BigDecimal> pricesPos, String nominal,
                                                           BigDecimal minPriceIncrement, String aciValue, String baseMoney) {
        log.info("Считаем стоимость позиции в портфеле");
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal valuePos4 = BigDecimal.ZERO;
        BigDecimal valuePos5 = BigDecimal.ZERO;
        BigDecimal valuePos7 = BigDecimal.ZERO;

        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal minPriceIncrementNew = minPriceIncrement
                    .multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
                    .multiply(minPriceIncrementNew);
                BigDecimal price = roundPrice
                    .add(new BigDecimal(aciValue));
                valuePos2 = new BigDecimal(steps.quantitySU29009RMFS6).multiply(price);
                log.info("Считаем стоимость позиции " + instrument.instrumentSU29009RMFS6 + "в портфеле: " + valuePos2);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentLKOH + "в портфеле: " + valuePos3);
            }
            if (pair.getKey().equals(instrument.instrumentSNGSP)) {
                valuePos4 = new BigDecimal(steps.quantitySNGSP).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentSNGSP + "в портфеле: " + valuePos4);
            }
            if (pair.getKey().equals(instrument.instrumentTRNFP)) {
                valuePos5 = new BigDecimal(steps.quantityTRNFP).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentTRNFP + "в портфеле: " + valuePos5);
            }
            if (pair.getKey().equals(instrument.instrumentUSD)) {
                valuePos7 = new BigDecimal(steps.quantityUSD).multiply((BigDecimal) pair.getValue());
                log.info("Считаем стоимость позиции " + instrument.instrumentUSD + "в портфеле: " + valuePos7);
            }
        }
        Map<PositionDateFromFireg, BigDecimal> positionIdMap = new HashMap<>();
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, instrument.typeSU29009RMFS6, instrument.sectorSU29009RMFS6, instrument.companySU29009RMFS6), valuePos2);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerLKOH, instrument.tradingClearingAccountLKOH, instrument.typeLKOH, instrument.sectorLKOH, instrument.companyLKOH), valuePos3);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerSNGSP, instrument.tradingClearingAccountSNGSP, instrument.typeSNGSP, instrument.sectorSNGSP, instrument.companySNGSP), valuePos4);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerTRNFP, instrument.tradingClearingAccountTRNFP, instrument.typeTRNFP, instrument.sectorTRNFP, instrument.companyTRNFP), valuePos5);
        positionIdMap.put(new PositionDateFromFireg(instrument.tickerUSD, instrument.tradingClearingAccountUSD, instrument.typeUSD, instrument.sectorUSD, instrument.companyUSD), valuePos7);
        return positionIdMap;
    }

    @Step("Считаем стоимость портфеля: ")
    BigDecimal getValuePortfolio(Map<String, BigDecimal> pricesPos, String nominal,
                                 BigDecimal minPriceIncrement, String aciValue, String baseMoney) {
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal valuePos4 = BigDecimal.ZERO;
        BigDecimal valuePos5 = BigDecimal.ZERO;
        BigDecimal valuePos6 = BigDecimal.ZERO;
        BigDecimal valuePos7 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(steps.quantitySBER).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal minPriceIncrementNew = minPriceIncrement
                    .multiply(new BigDecimal(nominal))
                    .scaleByPowerOfTen(-2);
                BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
                    .multiply(minPriceIncrementNew);
                BigDecimal price = roundPrice
                    .add(new BigDecimal(aciValue));
                valuePos2 = new BigDecimal(steps.quantitySU29009RMFS6).multiply(price);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(steps.quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSNGSP)) {
                valuePos4 = new BigDecimal(steps.quantitySNGSP).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentTRNFP)) {
                valuePos5 = new BigDecimal(steps.quantityTRNFP).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentESGR)) {
                valuePos6 = new BigDecimal(steps.quantityESGR).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentUSD)) {
                valuePos7 = new BigDecimal(steps.quantityUSD).multiply((BigDecimal) pair.getValue());
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




    //группируем данные по показателям
    @Step("Группируем данные по показателям Sectors: ")
    Map<String, BigDecimal> getSectors(Map<PositionDateFromFireg, BigDecimal> positionIdMap, String baseMoney) {
        Map<String, BigDecimal> sectors = positionIdMap.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getSector(),
                Map.Entry::getValue, BigDecimal::add));
        return sectors;
    }
    @Step("Группируем данные по показателям Types: ")
    Map<String, BigDecimal> getTypes(Map<PositionDateFromFireg, BigDecimal> positionIdMap, String baseMoney) {
        Map<String, BigDecimal> types = positionIdMap.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getType(),
                Map.Entry::getValue, BigDecimal::add));
        return types;
    }

    @Step("Группируем данные по показателям Companys: ")
    Map<String, BigDecimal> getCompanys(Map<PositionDateFromFireg, BigDecimal> positionIdMap, String baseMoney) {
        Map<String, BigDecimal> companys = positionIdMap.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getCompany(),
                Map.Entry::getValue, BigDecimal::add));
        return companys;
    }

    @Step("Проверяем расчитанные и олученные данные по долям sectors, types, companys: ")
    void checkParam(Map<String, BigDecimal> sectors, Map<String, BigDecimal> types, Map<String,
        BigDecimal> companys, LocalDateTime cut, LocalDateTime cutInCommand) {
        TreeMap treeMapForTypes =  new TreeMap<>(types);
        treeMapForTypes.entrySet().stream().sorted();
        TreeMap treeMapForCompanies =  new TreeMap<>(companys);
        treeMapForCompanies.entrySet().stream().sorted();
        TreeMap treeMapForSectors =  new TreeMap<>(sectors);
        treeMapForSectors.entrySet().stream().sorted();

        assertAll("Выполняем проверки",
            () -> assertThat("доли по секторам не равны", new TreeMap<>(masterPortfolioRate.getSectorToRateMap()), is(treeMapForSectors)),
            () -> assertThat("доли типам  не равны", new TreeMap<>(masterPortfolioRate.getTypeToRateMap()), is(treeMapForTypes)),
            () -> assertThat("доли по компаниям не равны", new TreeMap<>(masterPortfolioRate.getCompanyToRateMap()), is(treeMapForCompanies)),
            () -> assertThat("время cut не равно", cut, is(cutInCommand))
        );

//        assertAll("Выполняем проверки",
//            () -> assertThat("доли по секторам не равны", true, is(sectors.equals(masterPortfolioRate.getSectorToRateMap()))),
//            () -> assertThat("доли типам  не равны", true, is(types.equals(masterPortfolioRate.getTypeToRateMap()))),
//            () -> assertThat("доли по компаниям не равны", true, is(companys.equals(masterPortfolioRate.getCompanyToRateMap()))),
//            () -> assertThat("время cut не равно", true, is(cut.equals(cutInCommand)))
//        );
    }

    @Step("Рассчитываем стоимость позиции bond: ")
    BigDecimal valuePosBonds(String priceTs, String nominal, BigDecimal minPriceIncrement, String aciValue, BigDecimal valuePos) {
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price = roundPrice
            .add(new BigDecimal(aciValue));
        valuePos = new BigDecimal(steps.quantitySU29009RMFS6).multiply(price);
        return valuePos;
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

    void createMasterPortfoliosWithZero() {
        steps.createMasterPortfolioSevenPositionWithZero(3, 8, "16551.10", contractIdMaster, strategyId);
    }



    @Step("Создаем одну позицию для мастера в master_portfolio: ")
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
    @Step("Создаем две позиции для мастера в master_portfolio: ")
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

    @Step("Создаем портфель мастера в master_portfolio: ")
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


    // ожидаем версию портфеля slave
    void checkMasterPortfolioRate(UUID strategyId) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(5000);
            try {
                masterPortfolioRate = masterPortfolioRateDao.getMasterPortfolioRateByStrategyId(strategyId);
            } catch (Exception e){};

            if (masterPortfolioRate.getStrategyId() == null) {
                Thread.sleep(1000);
            }
        }
    }
    //исключаем группу позиций с деньгами
    @Step("Исключаем группу позиций с деньгами: ")
    private BigDecimal calculateSumWithoutMoneyGroup(Map<String, BigDecimal> map, String moneyGroupKey) {
        return map.entrySet()
            .stream()
            .filter(entry -> !entry.getKey().equals(moneyGroupKey))
            .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
