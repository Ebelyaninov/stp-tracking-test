package stpTrackingApi.getMasterPortfolio;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
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
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetMasterPortfolioResponse;
import ru.qa.tinkoff.swagger.tracking.model.MasterPortfolioPosition;
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
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getMasterPortfolio - Получение виртуального портфеля")
@Feature("TAP-11459")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class

})
public class GetMasterPortfolioTest {
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    ClientService clientService;
    @Autowired
    ProfileService profileService;
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
    StpTrackingApiSteps steps;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;


    StrategyApi strategyApi = ru.qa.tinkoff.swagger.tracking.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.Config.apiConfig()).strategy();
    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();

    String contractIdMaster;
    String SIEBEL_ID_MASTER = "5-192WBUXCI";
    UUID strategyId;
    public String aciValue;
    public String nominal;
    private List<String> list;



    public String ticker1 = "AAPL";
    public String tradingClearingAccount1 = "TKCBM_TCAB";
    public String quantity1 = "5";
    public String classCode1 = "SPBXM";
    public String briefName1 = "Apple";
    public String image1 = "US0378331005.png";
    public String type1 = "share";
    public String currency1 = "usd";

    public String ticker2 = "XS0191754729";
    public String tradingClearingAccount2 = "L01+00002F00";
    public String quantity2 = "7";
    public String classCode2 = "TQOD";
    public String briefName2 = "Gazprom";
    public String image2 = "RU0007661625.png";
    public String type2 = "bond";
    public String currency2 = "usd";

    public String ticker3 = "FB";
    public String tradingClearingAccount3 = "TKCBM_TCAB";
    public String quantity3 = "3";
    public String classCode3 = "SPBXM";
    public String instrument3 = ticker3 + "_" + classCode3;
    public String briefName3 = "Facebook";
    public String image3 = "US30303M1027.png";
    public String type3 = "share";
    public String currency3 = "usd";


    public String ticker4 = "USD000UTSTOM";
    public String tradingClearingAccount4 = "MB9885503216";
    public String quantity4 = "3000";
    public String classCode4 = "CETS";
    public String instrument4 = ticker3 + "_" + classCode3;
    public String briefName4 = "Доллар США";
    public String image4 = "USD.png";
    public String type4 = "currency";
    public String currency4 = "rub";




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

        });
    }
    @BeforeEach
    public void getDateBond() {
        if (list == null) {
            step("Получаем данные по облигации", () -> {
                list = steps.getPriceFromExchangePositionCache(ticker2, tradingClearingAccount2, SIEBEL_ID_MASTER);
                aciValue = list.get(0);
                nominal = list.get(1);
            });
        }
    }

    @Test
    @AllureId("1184370")
    @DisplayName("C1184370.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе active")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184370() {
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio1 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker1))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio2 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker2))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker3))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price1 = steps.getPriceFromExchangePositionPriceCache(ticker1, tradingClearingAccount1, "last", SIEBEL_ID_MASTER);
        String price2 = steps.getPriceFromExchangePositionPriceCache(ticker2, tradingClearingAccount2, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(ticker3, tradingClearingAccount3, "last", SIEBEL_ID_MASTER);
//        // получаем данные для расчета по облигациям
//        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
//            .instrumentIdPath(ticker2)
//            .idKindQuery("ticker")
//            .classCodeQuery(classCode2)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response);
//        String aciValue = resp.getBody().jsonPath().getString("[0].value");
//        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue1 = new BigDecimal(price1).multiply(new BigDecimal(quantity1));
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantity2));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantity3)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue1.add(positionValue2).add(positionValue3)
            .add(new BigDecimal("210.53"));
        //Рассчитываем positionRate position
        BigDecimal positionRate1 = positionValue1.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100")).setScale(2, RoundingMode.DOWN);
        BigDecimal positionRate2 = (positionValue2.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        BigDecimal positionRate3 = (positionValue3.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(positionRate1.add(positionRate2).add(positionRate3));
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(4));
        //проверяем данные по позициям
        checkParamPosition(MasterPortfolio1, ticker1, briefName1, image1, type1, quantity1, price1, currency1,
            positionValue1, positionRate1);
        checkParamPosition(MasterPortfolio2, ticker2, briefName2, image2, type2, quantity2, priceNominal2.toString(), currency2,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, ticker3, briefName3, image3, type3, quantity3, price3, currency3,
            positionValue3, positionRate3);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("210.53")));
    }


    @Test
    @AllureId("1185517")
    @DisplayName("C1185517.GetMasterPortfolio.Получение виртуального портфеля для стратегии. money маппим на currency\n")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1185517() {
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "300000.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker4, tradingClearingAccount4, quantity4);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "80190.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio4 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker4))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price4 = steps.getPriceFromExchangePositionPriceCache(ticker4, tradingClearingAccount4, "last", SIEBEL_ID_MASTER);
        //Рассчитываем positionValue position
        BigDecimal positionValue4 = new BigDecimal(price4).multiply(new BigDecimal(quantity4));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue4.add(new BigDecimal("80190.35"));
        //Рассчитываем positionRate position
        BigDecimal positionRate4 = positionValue4.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100")).setScale(2, RoundingMode.DOWN);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(positionRate4);
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(2));
        //проверяем данные по позициям
        checkParamPosition(MasterPortfolio4, ticker4, briefName4, image4, type4, quantity4, price4, currency4,
            positionValue4, positionRate4);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("rub"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("80190.35")));
    }


    @Test
    @AllureId("1176538")
    @DisplayName("C1176538.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе draft")
    @Subfeature("Успешные сценарии")
    @Description(" Метод для получения данных виртуального портфеля ведущего")
    void C1176538() {
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
//        createMasterPortfolios();
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100");
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(1));
        assertThat("positions не равно", getMasterPortfolioResponse.getPositions().size(), is(0));
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("2500.0")));
    }


    @Test
    @AllureId("1184401")
    @DisplayName("C1184401.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе active.нулевая позиция")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184401() {
        String ticker1 = "AAPL";
        String tradingClearingAccount1 = "TKCBM_TCAB";
        String quantity1 = "0";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2, ticker3, tradingClearingAccount3, quantity3);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio1 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker1))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio2 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker2))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker3))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(ticker2, tradingClearingAccount2, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(ticker3, tradingClearingAccount3, "last", SIEBEL_ID_MASTER);
        // получаем данные для расчета по облигациям
//        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
//            .instrumentIdPath(ticker2)
//            .idKindQuery("ticker")
//            .classCodeQuery(classCode2)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response);
//        String aciValue = resp.getBody().jsonPath().getString("[0].value");
//        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantity2));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantity3)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue2.add(positionValue3)
            .add(new BigDecimal("210.53"));
        //Рассчитываем positionRate position
        BigDecimal positionRate2 = (positionValue2.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        BigDecimal positionRate3 = (positionValue3.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(positionRate2.add(positionRate3));
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(4));
        assertThat("данные по нулевой позиции не равно", MasterPortfolio1.size(), is(0));
//        //проверяем данные по позициям
        checkParamPosition(MasterPortfolio2, ticker2, briefName2, image2, type2, quantity2, priceNominal2.toString(), currency2,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, ticker3, briefName3, image3, type3, quantity3, price3, currency3,
            positionValue3, positionRate3);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("210.53")));
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
    @AllureId("1176547")
    @DisplayName("C1176547.GetMasterPortfolio.Валидация запроса: x-app-name, x-app-version, x-platform")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1176547(String appName, String appVersion, String appPlatform) {
        strategyId = UUID.randomUUID();
        // вызываем метод getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (appName != null) {
            getMasterPortfolioResponse = getMasterPortfolioResponse.xAppNameHeader(appName);
        }
        if (appVersion != null) {
            getMasterPortfolioResponse = getMasterPortfolioResponse.xAppVersionHeader(appVersion);
        }
        if (appPlatform != null) {
            getMasterPortfolioResponse = getMasterPortfolioResponse.xPlatformHeader(appPlatform);
        }
        getMasterPortfolioResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1184175")
    @DisplayName("C1184175.GetMasterPortfolio.Валидация запроса: не передан заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184175() {
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401));
        getMasterPortfolioResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }


    @SneakyThrows
    @Test
    @AllureId("1184223")
    @DisplayName("C1184223.GetMasterPortfolio.Не удалось получить clientId из кэша clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184223() {
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader("7-192WBUXCI")
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterPortfolioResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1184310")
    @DisplayName("C1184310.GetMasterPortfolio.Стратегия не найдена")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184310() {
        strategyId = UUID.randomUUID();
        //формируем тело запроса метода getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterPortfolioResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("1184442")
    @DisplayName("C1184442.GetMasterPortfolio.Получение виртуального портфеля для не автора стратегии")
    @Subfeature("Альтернативные сценарии")
    @Description(" Метод для получения данных виртуального портфеля ведущего")
    void C1184442() throws JSONException {
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        // вызываем метод getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader("7-192WBUXCI")
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterPortfolioResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1184327")
    @DisplayName("C1184327.GetMasterPortfolio.Не найден портфель в таблице master_portfolio (Cassandra)")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184327() {
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем тело запроса метода getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterPortfolioResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1184334")
    @DisplayName("C1184334.GetMasterPortfolio.Не найдены данные по позиции в кэш exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184334() {
        String ticker1 = "TEST";
        String tradingClearingAccount1 = "TEST";
        String quantity1 = "5";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2, ticker3, tradingClearingAccount3, quantity3);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio1 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker1))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio2 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker2))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker3))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(ticker2, tradingClearingAccount2, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(ticker3, tradingClearingAccount3, "last", SIEBEL_ID_MASTER);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantity2));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantity3)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue2.add(positionValue3)
            .add(new BigDecimal("210.53"));
        //Рассчитываем positionRate position
        BigDecimal positionRate2 = (positionValue2.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        BigDecimal positionRate3 = (positionValue3.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(positionRate2.add(positionRate3));
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(4));
        //проверяем данные по позициям
        assertThat("master_portfolio.version текущего портфеля не равно", MasterPortfolio1.size(), is(0));
        checkParamPosition(MasterPortfolio2, ticker2, briefName2, image2, type2, quantity2, priceNominal2.toString(), currency2,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, ticker3, briefName3, image3, type3, quantity3, price3, currency3,
            positionValue3, positionRate3);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("210.53")));
    }


    @SneakyThrows
    @Test
    @AllureId("1184396")
    @DisplayName("C1184396.GetMasterPortfolio.Не найдены данные по позиции в кэш exchangePositionPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184396() {
        String ticker1 = "SPNV";
        String tradingClearingAccount1 = "SPNV";
        String quantity1 = "5";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title = "autotest" + String.valueOf(x);
        String description = "new test стратегия autotest";
        BigDecimal minPriceIncrement = new BigDecimal("0.0001");
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2, ticker3, tradingClearingAccount3, quantity3);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApi.getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio1 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker1))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio2 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker2))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(ticker3))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(ticker2, tradingClearingAccount2, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(ticker3, tradingClearingAccount3, "last", SIEBEL_ID_MASTER);
        // получаем данные для расчета по облигациям
//        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
//            .instrumentIdPath(ticker2)
//            .idKindQuery("ticker")
//            .classCodeQuery(classCode2)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response);
//        String aciValue = resp.getBody().jsonPath().getString("[0].value");
//        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantity2));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantity3)));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue2.add(positionValue3)
            .add(new BigDecimal("210.53"));
        //Рассчитываем positionRate position
        BigDecimal positionRate2 = (positionValue2.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        BigDecimal positionRate3 = (positionValue3.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100"))).setScale(2, RoundingMode.DOWN);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(positionRate2.add(positionRate3));
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(4));
        //проверяем данные по позициям
        assertThat("master_portfolio.version текущего портфеля не равно", MasterPortfolio1.size(), is(0));

        checkParamPosition(MasterPortfolio2, ticker2, briefName2, image2, type2, quantity2, priceNominal2.toString(), currency2,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, ticker3, briefName3, image3, type3, quantity3, price3, currency3,
            positionValue3, positionRate3);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("210.53")));
    }


    void createMasterPortfolios() {
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, ticker2, tradingClearingAccount2, quantity2, ticker3, tradingClearingAccount3, quantity3);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
    }

    void checkParamPosition(List<MasterPortfolioPosition> MasterPortfolio, String ticker, String briefName,
                            String image, String type, String quantity, String price, String currency,
                            BigDecimal positionValue, BigDecimal positionRate) {
        assertThat("ticker позиции не равно", MasterPortfolio.get(0).getExchangePosition().getTicker(), is(ticker));
        assertThat("briefName позиции не равно", MasterPortfolio.get(0).getExchangePosition().getBriefName(), is(briefName));
        assertThat("image позиции не равно", MasterPortfolio.get(0).getExchangePosition().getImage(), is(image));
        assertThat("type позиции не равно", MasterPortfolio.get(0).getExchangePosition().getType().getValue(), is(type));
        assertThat("quantity позиции не равно", MasterPortfolio.get(0).getQuantity().toString(), is(quantity));
        assertThat("lastPrice.value позиции не равно", MasterPortfolio.get(0).getLastPrice().getValue(), is(new BigDecimal(price)));
        assertThat("lastPrice.currency позиции не равно", MasterPortfolio.get(0).getLastPrice().getCurrency().getValue(), is(currency));
        assertThat("positionValue.value позиции не равно", MasterPortfolio.get(0).getValue().getValue(), is(positionValue));
        assertThat("positionValue.currency позиции не равно", MasterPortfolio.get(0).getValue().getCurrency().getValue(), is(currency));
        assertThat("rate не равно", MasterPortfolio.get(0).getRate(), is(positionRate));
    }

}
