package stpTrackingApi.getMasterStrategyAnalytics;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.model.GetMasterStrategyAnalyticsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@Slf4j
@Epic("getMasterStrategyAnalytics - Получение аналитики по стратегии")
@Feature("TAP-10862")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getMasterStrategyAnalytics")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class
})
public class GetMasterStrategyAnalyticsTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    StrategyTailValueDao strategyTailValueDao;
    @Autowired
    ApiCreator<AnalyticsApi> analyticsApiCreator;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;

    String contractIdMaster;
    String SIEBEL_ID_MASTER;
    UUID strategyId;
    UUID investIdMaster;
    MasterPortfolioValue masterPortfolioValue;
    StrategyTailValue strategyTailValue;
    String description = "new test стратегия autotest";
    BigDecimal minPriceIncrement = new BigDecimal("0.0001");
    public String quantityAAPL = "5";
    public String quantityXS0191754729 = "7";
    public String quantityFB = "3";
    public String quantityUSD = "3000";
    public String quantityYNDX = "3";
    public String aciValue;
    public String nominal;
    private List<String> list;

    @BeforeAll
    void getDataFromAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMaster;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(SIEBEL_ID_MASTER);
    }

    @BeforeEach
    public void getDateBond() {
        if (list == null) {
            step("Получаем данные по облигации", () -> {
                list = steps.getPriceFromExchangePositionCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, SIEBEL_ID_MASTER);
                aciValue = list.get(0);
                nominal = list.get(1);
            });
        }
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                trackingService.deleteStrategy(steps.strategyMaster);
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
            try {
                strategyTailValueDao.deleteStrategyTailValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingContractEvent(contractIdMaster);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideStringsForStrategyStatus() {
        return Stream.of(
            Arguments.of(StrategyStatus.active),
            Arguments.of(StrategyStatus.frozen)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForStrategyStatus")
    @AllureId("1186570")
    @DisplayName("C1186570.GetMasterStrategyAnalytics.Получение аналитики по стратегии.Стратегия в статусе active / frozen")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1186570(StrategyStatus strategyStatus) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            strategyStatus, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price1 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_MASTER, instrument.instrumentAAPL);
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue positionXS1589324075
        BigDecimal positionValue1 = new BigDecimal(price1).multiply(new BigDecimal(quantityAAPL));
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue1.add(positionValue2).add(positionValue3)
            .add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("9151.625446"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("9151.625446"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        checkParam(getMasterStrategyAnalyticsResponse, "3131.215341", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }


    @Test
    @AllureId("1197426")
    @DisplayName("C1197426.GetMasterStrategyAnalytics.Получение аналитики по стратегии.Нет данных в кеш masterPortfolioValueCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1197426() throws Exception {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price1 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_MASTER, instrument.instrumentAAPL);
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue1 = new BigDecimal(price1).multiply(new BigDecimal(quantityAAPL));
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue1.add(positionValue2).add(positionValue3)
            .add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        checkParam(getMasterStrategyAnalyticsResponse, "3131.215341", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }


    @Test
    @AllureId("1197525")
    @DisplayName("C1197525.GetMasterStrategyAnalytics. Получение аналитики по стратегии.Нет данных в кеш strategyAnalyticsCache")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1197525() throws Exception {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price1 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_MASTER, instrument.instrumentAAPL);
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue1 = new BigDecimal(price1).multiply(new BigDecimal(quantityAAPL));
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue1.add(positionValue2).add(positionValue3)
            .add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("9151.625446"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("9151.625446"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        checkParam(getMasterStrategyAnalyticsResponse, "0", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }


    @Test
    @AllureId("1193060")
    @DisplayName("C1193060.GetMasterStrategyAnalytics.Получение аналитики по стратегии.Стратегия в статусе draft")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1193060() throws Exception {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11",true,true, null);
        //создаем портфель в кассандра без позиций
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //проверяем полученные в ответе парамерты
        checkParam(getMasterStrategyAnalyticsResponse, "0", "usd", new BigDecimal("2500.0"), "2500.0", new BigDecimal("0"), new BigDecimal("0"),
            new BigDecimal("0"), new BigDecimal("0"));
    }


    @Test
    @AllureId("1191513")
    @DisplayName("C1191513.GetMasterStrategyAnalytics.Получение аналитики по стратегии.Нулевая позиция")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1191513() throws Exception {
        String quantity1 = "0";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue2.add(positionValue3).add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("9151.625446"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("9151.625446"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        //проверяем значения, которые вернул метод с рассчетами
        checkParam(getMasterStrategyAnalyticsResponse, "3131.215341", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }


    private static Stream<Arguments> provideStringsForHeaders() {
        return Stream.of(
            Arguments.of(null, "android", "4.5.6"),
            Arguments.of("trading-invest", null, "I.3.7"),
            Arguments.of("trading", "ios 8.1", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForHeaders")
    @AllureId("1186572")
    @DisplayName("C1186572.GetMasterStrategyAnalytics.Валидация входного запроса: x-app-name, x-app-version, x-platform")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1186572(String appName, String appVersion, String appPlatform) {
        strategyId = UUID.randomUUID();
        AnalyticsApi.GetMasterStrategyAnalyticsOper getMasterStrategyAnalytics = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (appName != null) {
            getMasterStrategyAnalytics = getMasterStrategyAnalytics.xAppNameHeader(appName);
        }
        if (appVersion != null) {
            getMasterStrategyAnalytics = getMasterStrategyAnalytics.xAppVersionHeader(appVersion);
        }
        if (appPlatform != null) {
            getMasterStrategyAnalytics = getMasterStrategyAnalytics.xPlatformHeader(appPlatform);
        }
        getMasterStrategyAnalytics.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategyAnalytics.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1192522")
    @DisplayName("C1192522.GetMasterStrategyAnalytics.Валидация входного запроса: x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1192522() {
        strategyId = UUID.randomUUID();
        AnalyticsApi.GetMasterStrategyAnalyticsOper getMasterStrategyAnalytics = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(401));
        getMasterStrategyAnalytics.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategyAnalytics.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }


    @SneakyThrows
    @Test
    @AllureId("1192541")
    @DisplayName("C1192541.GetMasterStrategyAnalytics.Не удалось получить clientId в кэше clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1192541() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        AnalyticsApi.GetMasterStrategyAnalyticsOper getMasterStrategyAnalytics = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("7-192WBUXCI")
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterStrategyAnalytics.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategyAnalytics.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1192822")
    @DisplayName("C1192822.GetMasterStrategyAnalytics.Не найдена стратегия в таблице strategy")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1192822() {
        strategyId = UUID.randomUUID();
        AnalyticsApi.GetMasterStrategyAnalyticsOper getMasterStrategyAnalytics = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterStrategyAnalytics.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategyAnalytics.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("1193579")
    @DisplayName("C1193579.GetMasterStrategyAnalytics.Не найден портфель в таблице master_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1193579() throws Exception {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // вызываем метод getMasterStrategyAnalytics
        AnalyticsApi.GetMasterStrategyAnalyticsOper getMasterStrategyAnalytics = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterStrategyAnalytics.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategyAnalytics.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("1194078")
    @DisplayName("C1194078.GetMasterStrategyAnalytics.Не найдены данные по позиции в кэш exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1194078() throws Exception {
        String ticker1 = "TEST";
        String tradingClearingAccount1 = "TEST";
        String quantity1 = "5";
        UUID positionId1 = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, positionId1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1, positionId1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1, positionId1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB,  instrument.positionIdFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue2.add(positionValue3).add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("9151.625446"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("9151.625446"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        //проверяем значения, которые вернул метод с рассчетами
        checkParam(getMasterStrategyAnalyticsResponse, "3131.215341", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }

    @Test
    @AllureId("1197191")
    @DisplayName("C1197191.GetMasterStrategyAnalytics.Не найдены данные по позиции в кэш exchangePositionPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1197191() throws Exception {
        String ticker1 = "SPNV";
        String tradingClearingAccount1 = "NDS000000001";
        UUID positionId1 = UUID.fromString("98b083fe-f3e3-483b-b32e-9eb242269c67");
        String quantity1 = "5";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, positionId1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1, positionId1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1, positionId1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromPriceCacheOrMD(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromPriceCacheOrMD(instrument.tickerFB, instrument.tradingClearingAccountFB,
            "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue2.add(positionValue3).add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("9151.625446"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("9151.625446"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        //проверяем значения, которые вернул метод с рассчетами
        checkParam(getMasterStrategyAnalyticsResponse, "3131.215341", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }


    @Test
    @AllureId("1197216")
    @DisplayName("C1197216.GetMasterStrategyAnalytics.Не найден портфель в masterPortfolioCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C1197216() throws Exception {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityAAPL);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB,  instrument.positionIdFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        AnalyticsApi.GetMasterStrategyAnalyticsOper getMasterStrategyAnalytics = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterStrategyAnalytics.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategyAnalytics.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }



    @Test
    @AllureId("2001633")
    @DisplayName("C2001633.GetMasterStrategyAnalytics.Не найдены данные по позиции в кэш по смене ticker")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения аналитических данных по торговой стратегии.")
    void C2001633() throws Exception {
        String ticker1 = "FB";
        String tradingClearingAccount1 = "TKCBM_TCAB";
        UUID positionId1 = UUID.fromString("fce134ae-bb91-498c-aa5d-4f49ad2e5392");
        String quantity1 = "5";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(10), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, positionId1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1, positionId1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1, positionId1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityAAPL);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        //создаем записи в master_portfolio_value за 10 дней
        createDatesMasterPortfolioValue();
        //создаем записи в strategy_tail_value
        createDatesStrategyTailValue();
        // вызываем метод getMasterStrategyAnalytics
        GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse = analyticsApiCreator.get().getMasterStrategyAnalytics()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategyAnalyticsResponse.class));
        //получаем значение prices из кеш ExchangePositionPrice
        String price1 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_MASTER, instrument.instrumentAAPL);
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER, instrument.instrumentXS0191754729);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER, instrument.instrumentFB);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue positionXS1589324075
        BigDecimal positionValue1 = new BigDecimal(price1).multiply(new BigDecimal(quantityAAPL));
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantity1)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue1.add(positionValue2).add(positionValue3)
            .add(new BigDecimal("210.53"));
        BigDecimal yield = portfolioValue.subtract(new BigDecimal("2500"));
        BigDecimal relativeYield = portfolioValue.divide(new BigDecimal("2500"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyYield = portfolioValue.subtract(new BigDecimal("9151.625446"));
        BigDecimal relativeDailyYield = portfolioValue.divide(new BigDecimal("9151.625446"), 4, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("1")).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        checkParam(getMasterStrategyAnalyticsResponse, "3131.215341", "usd", portfolioValue, "210.53", yield, dailyYield,
            relativeYield, relativeDailyYield);
    }



//методы для тестов

    void createMasterPortfolios() {
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB,  instrument.positionIdFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
    }


    void createMasterPortfolios1() {
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "100000.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            instrument.positionIdYNDX, quantityYNDX);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "84992.2",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            instrument.positionIdYNDX, quantityYNDX,
            instrument.tickerUSD, instrument.tradingClearingAccountUSD, instrument.positionIdUSD, quantityUSD);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "11327.2",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));


    }


    void createDateStrategyTailValue(UUID strategyId, int days, int hours, String value) {
        strategyTailValue = StrategyTailValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        strategyTailValueDao.insertIntoStrategyTailValue(strategyTailValue);
    }


    void createDatesStrategyTailValue() {
        createDateStrategyTailValue(strategyId, 5, 0, "2670.993224");
        createDateStrategyTailValue(strategyId, 4, 0, "4646.695446");
        createDateStrategyTailValue(strategyId, 3, 0, "3131.215341");
    }


    void createDatesMasterPortfolioValue() {
        createDateMasterPortfolioValue(strategyId, 10, 0, "2500");
        createDateMasterPortfolioValue(strategyId, 9, 0, "2500");
        createDateMasterPortfolioValue(strategyId, 8, 0, "2500");
        createDateMasterPortfolioValue(strategyId, 7, 0, "2497.75");
        createDateMasterPortfolioValue(strategyId, 6, 0, "2502.05");
        createDateMasterPortfolioValue(strategyId, 5, 0, "8670.998223");
        createDateMasterPortfolioValue(strategyId, 4, 0, "8666.695446");
        createDateMasterPortfolioValue(strategyId, 3, 0, "9151.625446");
        createDateMasterPortfolioValue(strategyId, 2, 0, "9151.625446");
        createDateMasterPortfolioValue(strategyId, 1, 0, "9151.625446");
        createDateMasterPortfolioValue(strategyId, 0, 0, "9151.625446");
    }


    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }


    void checkParam(GetMasterStrategyAnalyticsResponse getMasterStrategyAnalyticsResponse, String tailValue,
                    String currency, BigDecimal portfolioValue, String baseMoney, BigDecimal yield, BigDecimal dailyYield,
                    BigDecimal relativeYield, BigDecimal relativeDailyYield) {
        assertAll(
            () -> assertThat("tail.value  не равно", getMasterStrategyAnalyticsResponse.getTail().getValue(), is(new BigDecimal(tailValue))),
            () -> assertThat("tail.currency  не равно", getMasterStrategyAnalyticsResponse.getTail().getCurrency().getValue(), is(currency)),
            () -> assertThat("portfolio.value  не равно", getMasterStrategyAnalyticsResponse.getPortfolio().getValue(), is(portfolioValue)),
            () -> assertThat("portfolio.baseMoneyPositionQuantity  не равно", getMasterStrategyAnalyticsResponse.getPortfolio()
            .getBaseMoneyPositionQuantity(), is(new BigDecimal(baseMoney))),
            () -> assertThat("portfolio.currency  не равно", getMasterStrategyAnalyticsResponse.getPortfolio().getCurrency().getValue(), is(currency)),
            () -> assertThat("yield.value  не равно", getMasterStrategyAnalyticsResponse.getYield().getValue(), is(yield)),
            () -> assertThat("yield.currency  не равно", getMasterStrategyAnalyticsResponse.getYield().getCurrency().getValue(), is(currency)),
            () -> assertThat("dailyYield.value  не равно", getMasterStrategyAnalyticsResponse.getDailyYield().getValue(), is(dailyYield)),
            () -> assertThat("dailyYield.currency  не равно", getMasterStrategyAnalyticsResponse.getDailyYield().getCurrency().getValue(), is(currency)),
            () -> assertThat("relativeYield  не равно", getMasterStrategyAnalyticsResponse.getRelativeYield(), is(relativeYield)),
            () -> assertThat("relativeDailyYield  не равно", getMasterStrategyAnalyticsResponse.getRelativeDailyYield(), is(relativeDailyYield))
            );
    }

}
