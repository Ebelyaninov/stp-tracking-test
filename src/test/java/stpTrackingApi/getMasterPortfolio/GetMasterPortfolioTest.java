package stpTrackingApi.getMasterPortfolio;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
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
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
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
@Tags({@Tag("stp-tracking-api"), @Tag("getMasterPortfolio")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
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
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;

    String contractIdMaster;
    String SIEBEL_ID_MASTER;
    UUID strategyId;
    public String aciValue;
    public String nominal;
    private List<String> list;
    public String quantityAAPL = "5";
    public String quantityXS0191754729 = "7";
    public String quantityFB = "3";
    public String quantityUSD = "3000";
    String description = "стратегия autotest GetMasterPortfolio";
    String title;
    BigDecimal minPriceIncrement = new BigDecimal("0.0001");
    UUID investIdMaster;

    @BeforeAll
    void getDataFromAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMaster;
        title = steps.getTitleStrategy();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(SIEBEL_ID_MASTER);
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

        });
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


    private static Stream<Arguments> provideStrategyStatus(){
        return Stream.of(
            Arguments.of(StrategyStatus.active),
            Arguments.of(StrategyStatus.frozen)
        );
    }


    @ParameterizedTest
    @MethodSource("provideStrategyStatus")
    @AllureId("1184370")
    @DisplayName("C1184370.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе active")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184370(StrategyStatus status) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            status, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfolios();
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio1 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio2 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price1 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_MASTER);
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue1 = new BigDecimal(price1).multiply(new BigDecimal(quantityAAPL));
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
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
        checkParamPosition(MasterPortfolio1, instrument.tickerAAPL, instrument.briefNameAAPL,
            instrument.imageAAPL, instrument.typeAAPL, quantityAAPL, price1, instrument.currencyAAPL,
            positionValue1, positionRate1);
        checkParamPosition(MasterPortfolio2, instrument.tickerXS0191754729, instrument.briefNameXS0191754729,
            instrument.imageXS0191754729, instrument.typeXS0191754729, quantityXS0191754729, priceNominal2.toString(), instrument.currencyXS0191754729,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, instrument.tickerFB, instrument.briefNameFB, instrument.imageFB, instrument.typeFB, quantityFB, price3, instrument.currencyFB,
            positionValue3, positionRate3);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(), is(new BigDecimal("210.53")));
    }


    @ParameterizedTest
    @MethodSource("provideStrategyStatus")
    @AllureId("1185517")
    @DisplayName("C1185517.GetMasterPortfolio.Получение виртуального портфеля для стратегии. money маппим на currency\n")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1185517(StrategyStatus status) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            status, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "300000.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerUSD, instrument.tradingClearingAccountUSD, quantityUSD);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "80190.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio4 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerUSD))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price4 = steps.getPriceFromPriceCacheOrMD(instrument.tickerUSD, instrument.tradingClearingAccountUSD,
            "last", SIEBEL_ID_MASTER, instrument.instrumentUSD);
        //Рассчитываем positionValue position
        BigDecimal positionValue4 = new BigDecimal(price4).multiply(new BigDecimal(quantityUSD));
        //Рассчитываем portfolioValue
        BigDecimal portfolioValue = positionValue4.add(new BigDecimal("80190.35"));
        //Рассчитываем positionRate position
        BigDecimal positionRate4 = positionValue4.divide(portfolioValue, 4, RoundingMode.DOWN)
            .multiply(new BigDecimal("100")).setScale(2, RoundingMode.DOWN);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(positionRate4);
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(2));
        //проверяем данные по позициям
        checkParamPosition(MasterPortfolio4, instrument.tickerUSD, instrument.briefNameUSD, instrument.imageUSD, instrument.typeCurUSD, quantityUSD, price4, instrument.currencyUSD,
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
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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


    @ParameterizedTest
    @MethodSource("provideStrategyStatus")
    @AllureId("1184401")
    @DisplayName("C1184401.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе active.нулевая позиция")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184401(StrategyStatus status) {
        String quantity1 = "0";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            status, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolio1 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio2 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB, "last", SIEBEL_ID_MASTER);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
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
        checkParamPosition(MasterPortfolio2, instrument.tickerXS0191754729, instrument.briefNameXS0191754729,
            instrument.imageXS0191754729, instrument.typeXS0191754729, quantityXS0191754729, priceNominal2.toString(), instrument.currencyXS0191754729,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, instrument.tickerFB, instrument.briefNameFB, instrument.imageFB, instrument.typeFB, quantityFB, price3, instrument.currencyFB,
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
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        // вызываем метод getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //формируем тело запроса метода getMasterPortfolio
        StrategyApi.GetMasterPortfolioOper getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
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
        checkParamPosition(MasterPortfolio2, instrument.tickerXS0191754729, instrument.briefNameXS0191754729,
            instrument.imageXS0191754729, instrument.typeXS0191754729, quantityXS0191754729, priceNominal2.toString(), instrument.currencyXS0191754729,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, instrument.tickerFB, instrument.briefNameFB, instrument.imageFB, instrument.typeFB, quantityFB, price3, instrument.currencyFB,
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
        String tradingClearingAccount1 = "NDS000000001";
        String quantity1 = "5";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        List<MasterPortfolio.Position> masterOnePositions = steps.masterOnePositions(Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), ticker1, tradingClearingAccount1, quantity1);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), ticker1, tradingClearingAccount1,
            quantity1, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB, quantityFB);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "210.53",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
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
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolio3 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
        //получаем значение prices из кеш ExchangePositionPrice
        String price2 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "last", SIEBEL_ID_MASTER);
        String price3 = steps.getPriceFromExchangePositionPriceCache(instrument.tickerFB, instrument.tradingClearingAccountFB,
            "last", SIEBEL_ID_MASTER);
        //Пересчет цены облигаций в абсолютное значение
        BigDecimal priceNominal2 = steps.valuePosBonds(price2, nominal, minPriceIncrement, aciValue);
        //Рассчитываем positionValue position
        BigDecimal positionValue2 = priceNominal2.multiply(new BigDecimal(quantityXS0191754729));
        BigDecimal positionValue3 = (new BigDecimal(price3).multiply(new BigDecimal(quantityFB)));
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
        checkParamPosition(MasterPortfolio2, instrument.tickerXS0191754729, instrument.briefNameXS0191754729,
            instrument.imageXS0191754729, instrument.typeXS0191754729, quantityXS0191754729, priceNominal2.toString(), instrument.currencyXS0191754729,
            positionValue2, positionRate2);
        checkParamPosition(MasterPortfolio3, instrument.tickerFB, instrument.briefNameFB, instrument.imageFB, instrument.typeFB, quantityFB, price3, instrument.currencyFB,
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
            .now(ZoneOffset.UTC).minusDays(7).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, quantityAAPL);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterOnePositions, 2, "1958.35",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).toInstant()));
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "1229.3",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toInstant()));
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, quantityXS0191754729,
            instrument.tickerFB, instrument.tradingClearingAccountFB, quantityFB);
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
