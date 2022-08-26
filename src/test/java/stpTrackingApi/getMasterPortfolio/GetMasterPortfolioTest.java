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
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.CADBClientAnalytic.model.ClientPositionsResponse;
import ru.qa.tinkoff.swagger.CADBClientAnalytic.model.Item;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetMasterPortfolioResponse;
import ru.qa.tinkoff.swagger.tracking.model.MasterPortfolioPosition;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

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
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;


    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_MASTER_DRAFT;
    String SIEBEL_ID_MASTER_ZERO;
    UUID strategyId = UUID.fromString("d5f4effc-7e35-4552-9b07-221c86073fa9");
    MasterPortfolio masterPortfolio;

    public String quantityAAPL = "4";
    public String quantityXS0191754729 = "3";
    public String quantityALFAperp = "3";
    public String quantityTUSD = "200";
    public String quantityBYN = "1";
    public String quantityYNDX = "1";
    public String quantityGAZP = "0";
    String description = "стратегия autotest GetMasterPortfolio";
    String title;
    UUID investIdMaster;
    String contractIdMaster;

    @BeforeAll
    void getDataFromAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMasterPortfolio;

        title = steps.getTitleStrategy();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        steps.deleteDataFromDb(SIEBEL_ID_MASTER);
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


    private static Stream<Arguments> provideStrategyStatus() {
        return Stream.of(
            Arguments.of(StrategyStatus.active),
            Arguments.of(StrategyStatus.frozen)
        );
    }


    @ParameterizedTest
    @MethodSource("provideStrategyStatus")
    @SneakyThrows
    @AllureId("1184370")
    @DisplayName("C1184370.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе active/frozen")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184370(StrategyStatus status) {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMasterPortfolio;
        strategyId = UUID.fromString("d5f4effc-7e35-4552-9b07-221c86073fa9");
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            status, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(40.00),
            "TEST", "TEST11", true, true, null);
        //создаем портфель с позициями, по которым есть данные в CADB
        createMasterPortfolios(contractIdMaster, strategyId);
        //вызываем метод CADB getClientPositions
        ClientPositionsResponse dateCadb = steps.getClientPositionsCadb(investIdMaster, strategyId);
        //по getClientPositions фильтруем данные по каждой позиции отдельно
        List<Item> ClientPositionAAPL = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<Item> ClientPositionALFAperp = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        List<Item> ClientPositionTUSD = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerTUSD))
            .collect(Collectors.toList());
        List<Item> ClientPositionXS0191754729 = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям из ответа метода GetMasterPortfolio
        List<MasterPortfolioPosition> MasterPortfolioAAPL = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolioXS0191754729 = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolioALFAperp = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolioTUSD = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerTUSD))
            .collect(Collectors.toList());
        //получаем последнюю версию портфеля из кассандры
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //по masterPortfolio фильтруем данные по каждой позиции отдельно
        List<MasterPortfolio.Position> positionAAPL = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionXS0191754729 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionALFAperp = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionTUSD = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerTUSD))
            .collect(Collectors.toList());
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(masterPortfolio.getVersion()));
//         //проверяем данные по позициям
        checkParamPosition(MasterPortfolioAAPL, instrument.tickerAAPL, instrument.briefNameAAPL, instrument.imageAAPL, instrument.typeAAPL,
            positionAAPL.get(0).getQuantity(), ClientPositionAAPL.get(0).getPrices().getCurrentPrices().getUsd(), instrument.currencyAAPL,
            ClientPositionAAPL.get(0).getCurrentPositionAmounts().getUsd(), ClientPositionAAPL.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionAAPL.get(0).getPrices().getAveragePrices().getUsd(), ClientPositionAAPL.get(0).getPrices().getCurrentPrices().getUsd(),
            ClientPositionAAPL.get(0).getPrices().getFifoPrices().getUsd(), ClientPositionAAPL.get(0).getPositionFifoYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionFifoYields().getRelative().getUsd(), ClientPositionAAPL.get(0).getPositionDailyfifoYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionDailyfifoYields().getRelative().getUsd(), ClientPositionAAPL.get(0).getPositionAverageYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionAverageYields().getRelative().getUsd());
        checkParamPosition(MasterPortfolioXS0191754729, instrument.tickerXS0191754729, instrument.briefNameXS0191754729, instrument.imageXS0191754729, instrument.typeXS0191754729,
            positionXS0191754729.get(0).getQuantity(), ClientPositionXS0191754729.get(0).getPrices().getCurrentPrices().getUsd(), instrument.currencyXS0191754729,
            ClientPositionXS0191754729.get(0).getCurrentPositionAmounts().getUsd(), ClientPositionXS0191754729.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionXS0191754729.get(0).getPrices().getAveragePrices().getUsd(), ClientPositionXS0191754729.get(0).getPrices().getCurrentPrices().getUsd(),
            ClientPositionXS0191754729.get(0).getPrices().getFifoPrices().getUsd(), ClientPositionXS0191754729.get(0).getPositionFifoYields().getAbsolute().getUsd(),
            ClientPositionXS0191754729.get(0).getPositionFifoYields().getRelative().getUsd(), ClientPositionXS0191754729.get(0).getPositionDailyfifoYields().getAbsolute().getUsd(),
            ClientPositionXS0191754729.get(0).getPositionDailyfifoYields().getRelative().getUsd(), ClientPositionXS0191754729.get(0).getPositionAverageYields().getAbsolute().getUsd(),
            ClientPositionXS0191754729.get(0).getPositionAverageYields().getRelative().getUsd());
        checkParamPosition(MasterPortfolioALFAperp, instrument.tickerALFAperp, instrument.briefNameALFAperp, instrument.imageALFAperp, instrument.typeALFAperp,
            positionALFAperp.get(0).getQuantity(), ClientPositionALFAperp.get(0).getPrices().getCurrentPrices().getUsd(), instrument.currencyALFAperp,
            ClientPositionALFAperp.get(0).getCurrentPositionAmounts().getUsd(), ClientPositionALFAperp.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionALFAperp.get(0).getPrices().getAveragePrices().getUsd(), ClientPositionALFAperp.get(0).getPrices().getCurrentPrices().getUsd(),
            ClientPositionALFAperp.get(0).getPrices().getFifoPrices().getUsd(), ClientPositionALFAperp.get(0).getPositionFifoYields().getAbsolute().getUsd(),
            ClientPositionALFAperp.get(0).getPositionFifoYields().getRelative().getUsd(), ClientPositionALFAperp.get(0).getPositionDailyfifoYields().getAbsolute().getUsd(),
            ClientPositionALFAperp.get(0).getPositionDailyfifoYields().getRelative().getUsd(), ClientPositionALFAperp.get(0).getPositionAverageYields().getAbsolute().getUsd(),
            ClientPositionALFAperp.get(0).getPositionAverageYields().getRelative().getUsd());
        checkParamPosition(MasterPortfolioTUSD, instrument.tickerTUSD, instrument.briefNameTUSD, instrument.imageTUSD, instrument.typeTUSD,
            positionTUSD.get(0).getQuantity(), ClientPositionTUSD.get(0).getPrices().getCurrentPrices().getUsd(), instrument.currencyTUSD,
            ClientPositionTUSD.get(0).getCurrentPositionAmounts().getUsd(), ClientPositionTUSD.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionTUSD.get(0).getPrices().getAveragePrices().getUsd(), ClientPositionTUSD.get(0).getPrices().getCurrentPrices().getUsd(),
            ClientPositionTUSD.get(0).getPrices().getFifoPrices().getUsd(), ClientPositionTUSD.get(0).getPositionFifoYields().getAbsolute().getUsd(),
            ClientPositionTUSD.get(0).getPositionFifoYields().getRelative().getUsd(), ClientPositionTUSD.get(0).getPositionDailyfifoYields().getAbsolute().getUsd(),
            ClientPositionTUSD.get(0).getPositionDailyfifoYields().getRelative().getUsd(), ClientPositionTUSD.get(0).getPositionAverageYields().getAbsolute().getUsd(),
            ClientPositionTUSD.get(0).getPositionAverageYields().getRelative().getUsd());
//        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal rates = ClientPositionAAPL.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN)
            .add(ClientPositionALFAperp.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN))
            .add(ClientPositionTUSD.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN))
            .add(ClientPositionXS0191754729.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN));
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(rates);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(),
            is(masterPortfolio.getBaseMoneyPosition().getQuantity()));
        assertThat("portfolioAverageYield.absolute.value не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getAbsolute().getValue()
            , is(dateCadb.getPortfolioAverageYields().getAbsolute().getUsd()));
        assertThat("portfolioAverageYield.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getAbsolute().getCurrency().getValue()
            , is("usd"));
        assertThat("portfolioAverageYield.relative.value не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getRelative()
            , is(dateCadb.getPortfolioAverageYields().getRelative().getUsd()));
        assertThat("portfolioYieldPerDay.absolute.value не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getAbsolute().getValue()
            , is(dateCadb.getPortfolioDailyfifoYields().getAbsolute().getUsd()));
        assertThat("portfolioYieldPerDay.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getAbsolute().getCurrency().getValue()
            , is("usd"));
        assertThat("portfolioYieldPerDay.relative.value не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getRelative()
            , is(dateCadb.getPortfolioDailyfifoYields().getRelative().getUsd()));
        assertThat("portfolioYield.absolute.value не равно", getMasterPortfolioResponse.getPortfolioYield().getAbsolute().getValue()
            , is(dateCadb.getPortfolioFifoYields().getAbsolute().getUsd()));
        assertThat("portfolioYield.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioYield().getAbsolute().getCurrency().getValue()
            , is("usd"));
        assertThat("portfolioYield.relative.value не равно", getMasterPortfolioResponse.getPortfolioYield().getRelative()
            , is(dateCadb.getPortfolioFifoYields().getRelative().getUsd()));
        assertThat("fullAmount.bonds.value не равно", getMasterPortfolioResponse.getFullAmount().getBonds().getValue()
            , is(dateCadb.getCurrentAmountBonds().getUsd()));
        assertThat("fullAmount.bonds.currency не равно", getMasterPortfolioResponse.getFullAmount().getBonds().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.stocks.value не равно", getMasterPortfolioResponse.getFullAmount().getStocks().getValue()
            , is(dateCadb.getCurrentAmountStocks().getUsd()));
        assertThat("fullAmount.stocks.currency не равно", getMasterPortfolioResponse.getFullAmount().getStocks().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.etf.value не равно", getMasterPortfolioResponse.getFullAmount().getEtf().getValue()
            , is(dateCadb.getCurrentAmountEtfs().getUsd()));
        assertThat("fullAmount.etf.currency не равно", getMasterPortfolioResponse.getFullAmount().getEtf().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.currency.value не равно", getMasterPortfolioResponse.getFullAmount().getCurrency().getValue()
            , is(dateCadb.getCurrentAmountCurrencies().getUsd()));
        assertThat("fullAmount.currency.currency не равно", getMasterPortfolioResponse.getFullAmount().getCurrency().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.portfolio.value не равно", getMasterPortfolioResponse.getFullAmount().getPortfolio().getValue()
            , is(dateCadb.getCurrentAmounts().getUsd()));
        assertThat("fullAmount.portfolio.currency не равно", getMasterPortfolioResponse.getFullAmount().getPortfolio().getCurrency().getValue()
            , is("usd"));
    }


    @Test
    @AllureId("1184401")
    @DisplayName("C1184401.GetMasterPortfolio.Получение виртуального портфеля для стратегии. нулевая значение по позиции")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C1184401() {
        strategyId = UUID.fromString("52873954-ed43-45c4-86b1-ce72a7d63b27");
        SIEBEL_ID_MASTER_ZERO = stpSiebel.siebelIdApiMasterPortfolioZero;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER_ZERO);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER_ZERO, investIdMaster, null, contractIdMaster,
            null, ContractState.untracked,  strategyId, title, description, StrategyCurrency.rub,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,  StrategyStatus.active,
            0, LocalDateTime.now(), 1, "0.2", "0.04",   false,
            new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        // создаем портфель ведущего с позициями в кассандре  где у одной из позиций quantity = 0 и другая позиция это валюта
        List<MasterPortfolio.Position> masterThreePositions = steps.masterThreePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerGAZP, instrument.tradingClearingAccountGAZP,
            instrument.positionIdGAZP, quantityGAZP, instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            instrument.positionIdYNDX, quantityYNDX,
            instrument.tickerBYN, instrument.tradingClearingAccountBYN, instrument.positionIdBYN, quantityBYN);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterThreePositions, 4, "8937.2",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toInstant()));
        //вызываем метод CADB getClientPositions
        ClientPositionsResponse dateCadb = steps.getClientPositionsCadb(investIdMaster, strategyId);
        //по getClientPositions фильтруем данные по каждой позиции отдельно
        List<Item> ClientPositionYNDX = dateCadb.getItems().stream().filter(ms -> ms.getPositionUid().equals(instrument.positionIdYNDX))
            .collect(Collectors.toList());
        List<Item> ClientPositionBYN = dateCadb.getItems().stream().filter(ms -> ms.getPositionUid().equals(instrument.positionIdBYN))
            .collect(Collectors.toList());
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER_ZERO)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        // сохраняем данные по позициям
        List<MasterPortfolioPosition> MasterPortfolioYNDX = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerYNDX))
            .collect(Collectors.toList());
        List<MasterPortfolioPosition> MasterPortfolioBYN = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerBYN))
            .collect(Collectors.toList());
        //получаем последнюю версию портфеля из кассандры
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //по masterPortfolio фильтруем данные по каждой позиции отдельно
        List<MasterPortfolio.Position> positionYNDX = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerYNDX))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionBYN = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerBYN))
            .collect(Collectors.toList());
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(masterPortfolio.getVersion()));
//         //проверяем данные по позициям
        checkParamPosition(MasterPortfolioYNDX, instrument.tickerYNDX, instrument.briefNameYNDX, instrument.imageYNDX, instrument.typeYNDX,
            positionYNDX.get(0).getQuantity(), ClientPositionYNDX.get(0).getPrices().getCurrentPrices().getRub(), instrument.currencyYNDX,
            ClientPositionYNDX.get(0).getCurrentPositionAmounts().getRub(), ClientPositionYNDX.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionYNDX.get(0).getPrices().getAveragePrices().getRub(), ClientPositionYNDX.get(0).getPrices().getCurrentPrices().getRub(),
            ClientPositionYNDX.get(0).getPrices().getFifoPrices().getRub(), ClientPositionYNDX.get(0).getPositionFifoYields().getAbsolute().getRub(),
            ClientPositionYNDX.get(0).getPositionFifoYields().getRelative().getRub(), ClientPositionYNDX.get(0).getPositionDailyfifoYields().getAbsolute().getRub(),
            ClientPositionYNDX.get(0).getPositionDailyfifoYields().getRelative().getRub(), ClientPositionYNDX.get(0).getPositionAverageYields().getAbsolute().getRub(),
            ClientPositionYNDX.get(0).getPositionAverageYields().getRelative().getRub());
//        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal rates = ClientPositionYNDX.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN);
        BigDecimal baseMoneyPositionRate = new BigDecimal("100").subtract(rates);
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("rub"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(),
            is(masterPortfolio.getBaseMoneyPosition().getQuantity()));
        assertThat("portfolioAverageYield.absolute.value не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getAbsolute().getValue()
            , is(dateCadb.getPortfolioAverageYields().getAbsolute().getRub()));
        assertThat("portfolioAverageYield.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getAbsolute().getCurrency().getValue()
            , is("rub"));
        assertThat("portfolioAverageYield.relative.value не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getRelative()
            , is(dateCadb.getPortfolioAverageYields().getRelative().getRub()));
        assertThat("portfolioYieldPerDay.absolute.value не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getAbsolute().getValue()
            , is(dateCadb.getPortfolioDailyfifoYields().getAbsolute().getRub()));
        assertThat("portfolioYieldPerDay.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getAbsolute().getCurrency().getValue()
            , is("rub"));
        assertThat("portfolioYieldPerDay.relative.value не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getRelative()
            , is(dateCadb.getPortfolioDailyfifoYields().getRelative().getRub()));
        assertThat("portfolioYield.absolute.value не равно", getMasterPortfolioResponse.getPortfolioYield().getAbsolute().getValue()
            , is(dateCadb.getPortfolioFifoYields().getAbsolute().getRub()));
        assertThat("portfolioYield.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioYield().getAbsolute().getCurrency().getValue()
            , is("rub"));
        assertThat("portfolioYield.relative.value не равно", getMasterPortfolioResponse.getPortfolioYield().getRelative()
            , is(dateCadb.getPortfolioFifoYields().getRelative().getRub()));
        assertThat("fullAmount.bonds.value не равно", getMasterPortfolioResponse.getFullAmount().getBonds().getValue()
            , is(dateCadb.getCurrentAmountBonds().getRub()));
        assertThat("fullAmount.bonds.currency не равно", getMasterPortfolioResponse.getFullAmount().getBonds().getCurrency().getValue()
            , is("rub"));
        assertThat("fullAmount.stocks.value не равно", getMasterPortfolioResponse.getFullAmount().getStocks().getValue()
            , is(dateCadb.getCurrentAmountStocks().getRub()));
        assertThat("fullAmount.stocks.currency не равно", getMasterPortfolioResponse.getFullAmount().getStocks().getCurrency().getValue()
            , is("rub"));
        assertThat("fullAmount.etf.value не равно", getMasterPortfolioResponse.getFullAmount().getEtf().getValue()
            , is(dateCadb.getCurrentAmountEtfs().getRub()));
        assertThat("fullAmount.etf.currency не равно", getMasterPortfolioResponse.getFullAmount().getEtf().getCurrency().getValue()
            , is("rub"));
        assertThat("fullAmount.currency.value не равно", getMasterPortfolioResponse.getFullAmount().getCurrency().getValue()
            , is(dateCadb.getCurrentAmountCurrencies().getRub()));
        assertThat("fullAmount.currency.currency не равно", getMasterPortfolioResponse.getFullAmount().getCurrency().getCurrency().getValue()
            , is("rub"));
        assertThat("fullAmount.portfolio.value не равно", getMasterPortfolioResponse.getFullAmount().getPortfolio().getValue()
            , is(dateCadb.getCurrentAmounts().getRub()));
        assertThat("fullAmount.portfolio.currency не равно", getMasterPortfolioResponse.getFullAmount().getPortfolio().getCurrency().getValue()
            , is("rub"));

    }


    @Test
    @AllureId("1176538")
    @DisplayName("C1176538.GetMasterPortfolio.Получение виртуального портфеля для стратегии в статусе draft")
    @Subfeature("Успешные сценарии")
    @Description(" Метод для получения данных виртуального портфеля ведущего")
    void C1176538() {
        strategyId = UUID.fromString("5fe4063a-3a40-4a01-8329-d218a30ab87c");
        SIEBEL_ID_MASTER_DRAFT = stpSiebel.siebelIdApiMasterPortfolioDraft;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER_DRAFT);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER_DRAFT, investIdMaster, null,
            contractIdMaster, null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false,
            new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        // создаем портфель ведущего с позициями в кассандре без позиций
        steps.createMasterPortfolioWithOutPosition(10, 1, "2500.0", contractIdMaster, strategyId);
        //вызываем метод CADB getClientPositions
        ClientPositionsResponse dateCadb = steps.getClientPositionsCadb(investIdMaster, strategyId);
        // вызываем метод getMasterPortfolio
        GetMasterPortfolioResponse getMasterPortfolioResponse = strategyApiCreator.get().getMasterPortfolio()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER_DRAFT)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterPortfolioResponse.class));
        //получаем последнюю версию портфеля из кассандры
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //Рассчитываем positionRate baseMoneyPosition
        BigDecimal baseMoneyPositionRate = new BigDecimal("100");
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.rate позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getRate(), is(baseMoneyPositionRate));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(),
            is(masterPortfolio.getBaseMoneyPosition().getQuantity()));
        assertThat("portfolioAverageYield.absolute.value не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getAbsolute().getValue()
            , is(dateCadb.getPortfolioAverageYields().getAbsolute().getUsd()));
        assertThat("portfolioAverageYield.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getAbsolute().getCurrency().getValue()
            , is("usd"));
        assertThat("portfolioAverageYield.relative.value не равно", getMasterPortfolioResponse.getPortfolioAverageYield().getRelative()
            , is(dateCadb.getPortfolioAverageYields().getRelative().getUsd()));
        assertThat("portfolioYieldPerDay.absolute.value не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getAbsolute().getValue()
            , is(dateCadb.getPortfolioDailyfifoYields().getAbsolute().getUsd()));
        assertThat("portfolioYieldPerDay.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getAbsolute().getCurrency().getValue()
            , is("usd"));
        assertThat("portfolioYieldPerDay.relative.value не равно", getMasterPortfolioResponse.getPortfolioYieldPerDay().getRelative()
            , is(dateCadb.getPortfolioDailyfifoYields().getRelative().getUsd()));
        assertThat("portfolioYield.absolute.value не равно", getMasterPortfolioResponse.getPortfolioYield().getAbsolute().getValue()
            , is(dateCadb.getPortfolioFifoYields().getAbsolute().getUsd()));
        assertThat("portfolioYield.absolute.currency не равно", getMasterPortfolioResponse.getPortfolioYield().getAbsolute().getCurrency().getValue()
            , is("usd"));
        assertThat("portfolioYield.relative.value не равно", getMasterPortfolioResponse.getPortfolioYield().getRelative()
            , is(dateCadb.getPortfolioFifoYields().getRelative().getUsd()));
        assertThat("fullAmount.bonds.value не равно", getMasterPortfolioResponse.getFullAmount().getBonds().getValue()
            , is(dateCadb.getCurrentAmountBonds().getUsd()));
        assertThat("fullAmount.bonds.currency не равно", getMasterPortfolioResponse.getFullAmount().getBonds().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.stocks.value не равно", getMasterPortfolioResponse.getFullAmount().getStocks().getValue()
            , is(dateCadb.getCurrentAmountStocks().getUsd()));
        assertThat("fullAmount.stocks.currency не равно", getMasterPortfolioResponse.getFullAmount().getStocks().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.etf.value не равно", getMasterPortfolioResponse.getFullAmount().getEtf().getValue()
            , is(dateCadb.getCurrentAmountEtfs().getUsd()));
        assertThat("fullAmount.etf.currency не равно", getMasterPortfolioResponse.getFullAmount().getEtf().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.currency.value не равно", getMasterPortfolioResponse.getFullAmount().getCurrency().getValue()
            , is(dateCadb.getCurrentAmountCurrencies().getUsd()));
        assertThat("fullAmount.currency.currency не равно", getMasterPortfolioResponse.getFullAmount().getCurrency().getCurrency().getValue()
            , is("usd"));
        assertThat("fullAmount.portfolio.value не равно", getMasterPortfolioResponse.getFullAmount().getPortfolio().getValue()
            , is(dateCadb.getCurrentAmounts().getUsd()));
        assertThat("fullAmount.portfolio.currency не равно", getMasterPortfolioResponse.getFullAmount().getPortfolio().getCurrency().getValue()
            , is("usd"));

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
        steps.createClientWithContractAndStrategy(stpSiebel.siebelIdApiMaster, investIdMaster, null,
            contractIdMaster, null, ContractState.untracked,  strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11",
            true, true, null);
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
        strategyId = UUID.fromString("d5f4effc-7e35-4552-9b07-221c86073fa9");
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMasterPortfolio;
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        String ticker1 = "TEST";
        String tradingClearingAccount1 = "TEST";
        UUID positionId1 = UUID.randomUUID();
        String quantity1 = "5";
//        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null, contractIdMaster,
            null, ContractState.untracked,  strategyId, title, description, StrategyCurrency.usd,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative, StrategyStatus.active,
            0, LocalDateTime.now(), 1, "0.2", "0.04", false,
            new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        createMasterPortfoliosWithOutPositionId(contractIdMaster, strategyId, ticker1, tradingClearingAccount1, positionId1, quantity1);
        //вызываем метод CADB getClientPositions
        ClientPositionsResponse dateCadb = steps.getClientPositionsCadb(investIdMaster, strategyId);
        //по getClientPositions фильтруем данные по каждой позиции отдельно
        List<Item> ClientPositionAAPL = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
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
        List<MasterPortfolioPosition> MasterPortfolioAAPL = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        //получаем последнюю версию портфеля из кассандры
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //по masterPortfolio фильтруем данные по каждой позиции отдельно
        List<MasterPortfolio.Position> positionAAPL = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(masterPortfolio.getVersion()));
//         //проверяем данные по позициям
        checkParamPosition(MasterPortfolioAAPL, instrument.tickerAAPL, instrument.briefNameAAPL, instrument.imageAAPL, instrument.typeAAPL,
            positionAAPL.get(0).getQuantity(), ClientPositionAAPL.get(0).getPrices().getCurrentPrices().getUsd(), instrument.currencyAAPL,
            ClientPositionAAPL.get(0).getCurrentPositionAmounts().getUsd(), ClientPositionAAPL.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionAAPL.get(0).getPrices().getAveragePrices().getUsd(), ClientPositionAAPL.get(0).getPrices().getCurrentPrices().getUsd(),
            ClientPositionAAPL.get(0).getPrices().getFifoPrices().getUsd(), ClientPositionAAPL.get(0).getPositionFifoYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionFifoYields().getRelative().getUsd(), ClientPositionAAPL.get(0).getPositionDailyfifoYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionDailyfifoYields().getRelative().getUsd(), ClientPositionAAPL.get(0).getPositionAverageYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionAverageYields().getRelative().getUsd());
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(),
            is(masterPortfolio.getBaseMoneyPosition().getQuantity()));
    }


    @SneakyThrows
    @Test
    @AllureId("2084173")
    @DisplayName("C2084173.GetMasterPortfolio.Mетод CADB getClientPositions вернул ошибку")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C2084173() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(stpSiebel.siebelIdApiMaster, investIdMaster, null,
            contractIdMaster, null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11",
            true, true, null);
        createMasterPortfolios(contractIdMaster, strategyId);
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
    @AllureId("2001631")
    @DisplayName("C2001631.GetMasterPortfolio.Не найдены данные по позиции в кэш по смене ticker")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C2001631() {
        strategyId = UUID.fromString("d5f4effc-7e35-4552-9b07-221c86073fa9");
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMasterPortfolio;
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        String ticker1 = "AAPLTES";
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null,
            contractIdMaster, null, ContractState.untracked,  strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11",
            true, true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        List<MasterPortfolio.Position> masterFourPositions = steps.masterFourPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(15).toInstant()), ticker1, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, quantityALFAperp,
            instrument.tickerTUSD, instrument.tradingClearingAccountTUSD, instrument.positionIdTUSD, quantityTUSD);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterFourPositions, 7, "6469.901249",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant()));
        //вызываем метод CADB getClientPositions
        ClientPositionsResponse dateCadb = steps.getClientPositionsCadb(investIdMaster, strategyId);
        //по getClientPositions фильтруем данные по каждой позиции отдельно
        List<Item> ClientPositionAAPL = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
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
        List<MasterPortfolioPosition> MasterPortfolioAAPL = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        //получаем последнюю версию портфеля из кассандры
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //по masterPortfolio фильтруем данные по каждой позиции отдельно
        List<MasterPortfolio.Position> positionAAPL = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker1))
            .collect(Collectors.toList());
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(masterPortfolio.getVersion()));
//         //проверяем данные по позициям
        checkParamPosition(MasterPortfolioAAPL, instrument.tickerAAPL, instrument.briefNameAAPL, instrument.imageAAPL, instrument.typeAAPL,
            positionAAPL.get(0).getQuantity(), ClientPositionAAPL.get(0).getPrices().getCurrentPrices().getUsd(), instrument.currencyAAPL,
            ClientPositionAAPL.get(0).getCurrentPositionAmounts().getUsd(), ClientPositionAAPL.get(0).getPortfolioPercent().setScale(2, RoundingMode.DOWN),
            ClientPositionAAPL.get(0).getPrices().getAveragePrices().getUsd(), ClientPositionAAPL.get(0).getPrices().getCurrentPrices().getUsd(),
            ClientPositionAAPL.get(0).getPrices().getFifoPrices().getUsd(), ClientPositionAAPL.get(0).getPositionFifoYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionFifoYields().getRelative().getUsd(), ClientPositionAAPL.get(0).getPositionDailyfifoYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionDailyfifoYields().getRelative().getUsd(), ClientPositionAAPL.get(0).getPositionAverageYields().getAbsolute().getUsd(),
            ClientPositionAAPL.get(0).getPositionAverageYields().getRelative().getUsd());
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(),
            is(masterPortfolio.getBaseMoneyPosition().getQuantity()));
    }


    @SneakyThrows
    @Test
    @AllureId("2084326")
    @DisplayName("C2084326.GetMasterPortfolio.Не найдены данные по позиции в CADB")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения данных виртуального портфеля ведущего")
    void C2084326() {
        strategyId = UUID.fromString("d5f4effc-7e35-4552-9b07-221c86073fa9");
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMasterPortfolio;
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, null,
            contractIdMaster, null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11",
            true, true, null);
        // создаем портфель ведущего с позициями в кассандре  за разные даты с разными бумагами
        List<MasterPortfolio.Position> masterFourPositions = steps.masterFourPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(15).toInstant()), instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            instrument.positionIdABBV, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, quantityALFAperp,
            instrument.tickerTUSD, instrument.tradingClearingAccountTUSD, instrument.positionIdTUSD, quantityTUSD);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterFourPositions, 7, "6469.901249",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant()));
        //вызываем метод CADB getClientPositions
        ClientPositionsResponse dateCadb = steps.getClientPositionsCadb(investIdMaster, strategyId);
        //по getClientPositions фильтруем данные по каждой позиции отдельно
        List<Item> ClientPositionAAPL = dateCadb.getItems().stream().filter(ms -> ms.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
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
        List<MasterPortfolioPosition> MasterPortfolioAAPL = getMasterPortfolioResponse.getPositions().stream()
            .filter(ms -> ms.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        //получаем последнюю версию портфеля из кассандры
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //по masterPortfolio фильтруем данные по каждой позиции отдельно
        List<MasterPortfolio.Position> positionAAPL = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("master_portfolio.version текущего портфеля не равно", getMasterPortfolioResponse.getVersion(), is(masterPortfolio.getVersion()));
//         //проверяем что не найденную позицию отфильтровали
        assertThat("позиция не равно", MasterPortfolioAAPL.size(), is(0));
        //проверяем данные по базовой валюте
        assertThat("baseMoneyPosition.currency позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getCurrency().getValue(), is("usd"));
        assertThat("baseMoneyPosition.quantity позиции не равно", getMasterPortfolioResponse.getBaseMoneyPosition().getQuantity(),
            is(masterPortfolio.getBaseMoneyPosition().getQuantity()));
    }


    void createMasterPortfolios(String contractIdMaster, UUID strategyId) {
        List<MasterPortfolio.Position> masterFourPositions = steps.masterFourPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(15).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, quantityALFAperp,
            instrument.tickerTUSD, instrument.tradingClearingAccountTUSD, instrument.positionIdTUSD, quantityTUSD);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterFourPositions, 7, "6469.901249",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant()));
    }


    void createMasterPortfoliosWithOutPositionId(String contractIdMaster, UUID strategyId,
                                                 String ticker, String tradingClearingAccount, UUID positionId, String quantity) {
        List<MasterPortfolio.Position> masterFourPositions = steps.masterFivePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(15).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, quantityAAPL, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            instrument.positionIdXS0191754729, quantityXS0191754729,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, quantityALFAperp,
            instrument.tickerTUSD, instrument.tradingClearingAccountTUSD, instrument.positionIdTUSD, quantityTUSD,
            ticker, tradingClearingAccount, positionId, quantity);
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterFourPositions, 7, "6469.901249",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant()));
    }

    void checkParamPosition(List<MasterPortfolioPosition> MasterPortfolio, String ticker, String briefName,
                            String image, String type, BigDecimal quantity, BigDecimal price, String currency,
                            BigDecimal positionValue, BigDecimal positionRate, BigDecimal averagePrice,
                            BigDecimal currentPrice, BigDecimal avgCostPrice, BigDecimal yieldAbsolute,
                            BigDecimal yieldRelative, BigDecimal yieldPerDayAbsolute, BigDecimal yieldPerDayRelative,
                            BigDecimal yieldAverageAbsolute, BigDecimal yieldAverageRelative) {
        assertThat("ticker позиции не равно", MasterPortfolio.get(0).getExchangePosition().getTicker(), is(ticker));
        assertThat("briefName позиции не равно", MasterPortfolio.get(0).getExchangePosition().getBriefName(), is(briefName));
        assertThat("image позиции не равно", MasterPortfolio.get(0).getExchangePosition().getImage(), is(image));
        assertThat("type позиции не равно", MasterPortfolio.get(0).getExchangePosition().getType().getValue(), is(type));
        assertThat("quantity позиции не равно", MasterPortfolio.get(0).getQuantity(), is(quantity));
        assertThat("lastPrice.value позиции не равно", MasterPortfolio.get(0).getLastPrice().getValue(), is(price));
        assertThat("lastPrice.currency позиции не равно", MasterPortfolio.get(0).getLastPrice().getCurrency().getValue(), is(currency));
        assertThat("positionValue.value позиции не равно", MasterPortfolio.get(0).getValue().getValue(), is(positionValue));
        assertThat("positionValue.currency позиции не равно", MasterPortfolio.get(0).getValue().getCurrency().getValue(), is(currency));
        assertThat("rate не равно", MasterPortfolio.get(0).getRate(), is(positionRate));
        assertThat("prices.averagePositionPrice.value позиции не равно", MasterPortfolio.get(0).getPrices().getAveragePositionPrice().getValue(), is(averagePrice));
        assertThat("prices.averagePositionPrice.currency позиции не равно", MasterPortfolio.get(0).getPrices().getAveragePositionPrice().getCurrency().getValue(), is(currency));
        assertThat("prices.currentPrice.value позиции не равно", MasterPortfolio.get(0).getPrices().getCurrentPrice().getValue(), is(currentPrice));
        assertThat("prices.currentPrice.currency позиции не равно", MasterPortfolio.get(0).getPrices().getCurrentPrice().getCurrency().getValue(), is(currency));
        assertThat("prices.avgCostPrice.value позиции не равно", MasterPortfolio.get(0).getPrices().getAvgCostPrice().getAverage().getValue(), is(avgCostPrice));
        assertThat("prices.avgCostPrice.currency позиции не равно", MasterPortfolio.get(0).getPrices().getAvgCostPrice().getAverage().getCurrency().getValue(), is(currency));
        assertThat("yields.yield.absolute.value позиции не равно", MasterPortfolio.get(0).getYields().getYield().getAbsolute().getValue(), is(yieldAbsolute));
        assertThat("yields.yield.absolute.currency позиции не равно", MasterPortfolio.get(0).getYields().getYield().getAbsolute().getCurrency().getValue(), is(currency));
        assertThat("yields.yield.relative позиции не равно", MasterPortfolio.get(0).getYields().getYield().getRelative(), is(yieldRelative));
        assertThat("yields.yieldPerDay.absolute.value позиции не равно", MasterPortfolio.get(0).getYields().getYieldPerDay().getAbsolute().getValue(), is(yieldPerDayAbsolute));
        assertThat("yields.yieldPerDay.absolute.currency позиции не равно", MasterPortfolio.get(0).getYields().getYieldPerDay().getAbsolute().getCurrency().getValue(), is(currency));
        assertThat("yields.yieldPerDay.relative позиции не равно", MasterPortfolio.get(0).getYields().getYieldPerDay().getRelative(), is(yieldPerDayRelative));
        assertThat("yields.yieldAverage.absolute.value позиции не равно", MasterPortfolio.get(0).getYields().getYieldAverage().getAbsolute().getValue(), is(yieldAverageAbsolute));
        assertThat("yields.yieldAverage.absolute.currency позиции не равно", MasterPortfolio.get(0).getYields().getYieldAverage().getAbsolute().getCurrency().getValue(), is(currency));
        assertThat("yields.yieldAverage.relative позиции не равно", MasterPortfolio.get(0).getYields().getYieldAverage().getRelative(), is(yieldAverageRelative));
    }
}
