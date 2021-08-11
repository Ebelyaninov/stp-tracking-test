package stpTrackingApi.getStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.*;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetStrategyResponse;
import ru.qa.tinkoff.swagger.tracking.model.GetStrategyResponseCharacteristics;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getStrategy - Получение информации по торговой стратегии")
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
public class GetStrategyTest {
    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterPortfolioMaxDrawdownDao masterPortfolioMaxDrawdownDao;
    @Autowired
    MasterPortfolioPositionRetentionDao masterPortfolioPositionRetentionDao;
    @Autowired
    MasterPortfolioRateDao masterPortfolioRateDao;
    @Autowired
    MasterPortfolioTopPositionsDao masterPortfolioTopPositionsDao;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    SignalFrequencyDao signalFrequencyDao;
    @Autowired
    SignalsCountDao signalsCountDao;
    @Autowired
    StrategyTailValueDao strategyTailValueDao;

    Client clientMaster;
    Contract contractMaster;
    Strategy strategyMaster;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    Strategy strategy;
    MasterPortfolioMaxDrawdown masterPortfolioMaxDrawdown;
    MasterPortfolioPositionRetention masterPortfolioPositionRetention;
    MasterPortfolioRate masterPortfolioRate;
    MasterPortfolioTopPositions masterPortfolioTopPositions;
    SignalFrequency signalFrequency;
    SignalsCount signalsCount;
    StrategyTailValue strategyTailValue;

    String contractIdMaster;
    UUID strategyId;
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();

    String siebelIdMaster = "1-7XOAYPX";

    MasterPortfolioValue masterPortfolioValue;


    String tickerShare = "SBER";
    String tradingClearingAccountShare = "L01+00002F00";
    String quantityShare = "30";

    String tickerEtf = "FXDE";
    String tradingClearingAccountEtf = "L01+00002F00";
    String quantityEtf = "5";

    String tickerBond = "SU29009RMFS6";
    String tradingClearingAccountBond = "L01+00002F00";
    String quantityBond = "7";

    String tickerMoney = "USD000UTSTOM";
    String tradingClearingAccountMoney = "MB9885503216";
    String quantityMoney = "2000";

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
                masterPortfolioMaxDrawdownDao.deleteMasterPortfolioMaxDrawdownByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioPositionRetentionDao.deleteMasterPortfolioPositionRetention(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioRateDao.deleteMasterPortfolioRateByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioTopPositionsDao.deleteMasterPortfolioTopPositionsByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                signalFrequencyDao.deleteSignalFrequencyByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                signalsCountDao.deleteSignalsCountByStratedyId(strategyId);
            } catch (Exception e) {
            }
            try {
                strategyTailValueDao.deleteStrategyTailValueByStrategyId(strategyId);
            } catch (Exception e) {
            }

        });
    }

    @Test
    @AllureId("1087190")
    @DisplayName("C1087190.GetStrategy.Получение данных торговой стратегии c типом характеристик условий " +
        "подключения к стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1087190() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategyFee(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), "0.3", "0.05");
        //создаем запись в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        GetStrategyResponse getStrategy = strategyApi.getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategyMaster = strategyService.getStrategy(strategyId);
        List<GetStrategyResponseCharacteristics> strategyCharacteristics =
            getStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getType().getValue().equals("condition"))
                .collect(Collectors.toList());
        assertThat("Тип характеристики не равно", strategyCharacteristics.get(0).getType(), is(GetStrategyResponseCharacteristics.TypeEnum.CONDITION));
        assertThat("title не равно", strategyCharacteristics.get(0).getItems().get(0).getTitle(), is("Комиссия за управление"));
        assertThat("value не равно", strategyCharacteristics.get(0).getItems().get(0).getValue(), is("5.0 %"));
        assertThat("title не равно", strategyCharacteristics.get(0).getItems().get(1).getTitle(), is("Комиссия за результат"));
        assertThat("value не равно", strategyCharacteristics.get(0).getItems().get(1).getValue(), is("30.0 %"));
    }




    @Test
    @AllureId("1115576")
    @DisplayName("C1115576.GetStrategy.Не найдена стратегия в strategy")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1115576() throws JSONException {
        strategyId = UUID.randomUUID();
        StrategyApi.GetStrategyOper getStrategy = strategyApi.getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422));
        getStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("StrategyNotFound"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    public void createMasterPortfolio(String contractIdMaster, UUID strategyId, int version,
                                      String money, List<MasterPortfolio.Position> positionList) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }

    //позиции в портфеле мастера с разными типами инструментов
    public List<MasterPortfolio.Position> createListMasterPosition(Date date, int lastChangeDetectedVersion, Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal(quantityShare))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerEtf)
            .tradingClearingAccount(tradingClearingAccountEtf)
            .quantity(new BigDecimal(quantityEtf))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal(quantityBond))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerMoney)
            .tradingClearingAccount(tradingClearingAccountMoney)
            .quantity(new BigDecimal(quantityMoney))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }


    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }


    void createDateMasterPortfolioMaxDrawdown(UUID strategyId, int days, int hours, String value) {
        masterPortfolioMaxDrawdown = MasterPortfolioMaxDrawdown.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioMaxDrawdownDao.insertIntoMasterPortfolioMaxDrawdown(masterPortfolioMaxDrawdown);
    }

    void createDateSignalFrequency(UUID strategyId, int days, int hours, int count) {
        signalFrequency = SignalFrequency.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .count(count)
            .build();
        signalFrequencyDao.insertIntoSignalFrequency(signalFrequency);
    }


    void createDateSignalsCount(UUID strategyId, int days, int hours, int value) {
        signalsCount = SignalsCount.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(value)
            .build();
        signalsCountDao.insertIntoSignalsCount(signalsCount);
    }


    void createDateStrategyTailValue(UUID strategyId, int days, int hours, String value) {
        strategyTailValue = StrategyTailValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        strategyTailValueDao.insertIntoStrategyTailValue(strategyTailValue);
    }


    void createDateMasterPortfolioPositionRetention(UUID strategyId, int days, int hours, String value) {
        masterPortfolioPositionRetention = MasterPortfolioPositionRetention.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(value)
            .build();
        masterPortfolioPositionRetentionDao.insertIntoMasterPortfolioPositionRetention(masterPortfolioPositionRetention);
    }


    void createDateMasterPortfolioRate(UUID strategyId, int days, int hours) {
        Map<String, BigDecimal> companyToRateMap = new HashMap<>();
        companyToRateMap.put("Денежные средства", new BigDecimal("0.1082"));
        companyToRateMap.put("Лукойл", new BigDecimal("0.0598"));
        companyToRateMap.put("ОФЗ", new BigDecimal("0.0040"));
        companyToRateMap.put("РСХБ Управление Активами", new BigDecimal("0.0090"));
        companyToRateMap.put("Сбер Банк", new BigDecimal("0.0185"));
        companyToRateMap.put("Сургутнефтегаз", new BigDecimal("0.0057"));
        companyToRateMap.put("Транснефть", new BigDecimal("0.7948"));

        Map<String, BigDecimal> sectorToRateMap = new HashMap<>();
        companyToRateMap.put("energy", new BigDecimal("0.8603"));
        companyToRateMap.put("financial", new BigDecimal("0.0185"));
        companyToRateMap.put("government", new BigDecimal("0.0040"));
        companyToRateMap.put("money", new BigDecimal("0.1082"));
        companyToRateMap.put("other", new BigDecimal("0.0090"));

        Map<String, BigDecimal> typeToRateMap = new HashMap<>();
        companyToRateMap.put("bond", new BigDecimal("0.0040"));
        companyToRateMap.put("etf", new BigDecimal("0.0090"));
        companyToRateMap.put("money", new BigDecimal("0.1082"));
        companyToRateMap.put("share", new BigDecimal("0.8788"));

        masterPortfolioRate = MasterPortfolioRate.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .companyToRateMap(companyToRateMap)
            .sectorToRateMap(sectorToRateMap)
            .typeToRateMap(typeToRateMap)
            .build();

        masterPortfolioRateDao.insertIntoMasterPortfolioRate(masterPortfolioRate);
    }


    void createDateMasterPortfolioTopPositions(UUID strategyId, int days, int hours) {

//        SlavePortfolio.BaseMoneyPosition  baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
//            .quantity(baseMoney)
//            .changedAt(date)
//            .build();
//
//        MasterPortfolioTopPositions.TopPositions topPositions = MasterPortfolioTopPositions.TopPositions.builder()
//            .ticker(tickerShare)
//            .tradingClearingAccount(tradingClearingAccountShare)
//            .signalsCount(3)
//            .build();

//        MasterPortfolioTopPositions.TopPositions.TopPositionsBuilder baseMoneyPosition = MasterPortfolioTopPositions.TopPositions.builder()
//            .ticker(tickerShare)
//            .tradingClearingAccount(tradingClearingAccountShare)
//            .signalsCount(3);


//        List<TopPosition> topPositions =new ArrayList<>();
//        topPositions.add(TopPosition.builder()
//            .ticker(tickerShare)
//            .tradingClearingAccount(tradingClearingAccountShare)
//            .signalsCount(3)
//            .build());

        List<MasterPortfolioTopPositions.TopPositions> topPositions = new ArrayList<>();
        topPositions.add(MasterPortfolioTopPositions.TopPositions.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .signalsCount(3)
            .build());
        masterPortfolioTopPositions = MasterPortfolioTopPositions.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .positions(topPositions)
            .build();
        masterPortfolioTopPositionsDao.insertIntoMasterPortfolioTopPositions(masterPortfolioTopPositions);

    }

    void createMasterSignal(int minusDays, int minusHours, int version, UUID strategyId, String ticker, String tradingClearingAccount,
                            String price, String quantity, int action) {
        LocalDateTime time = LocalDateTime.now().minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyId)
            .version(version)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .action((byte) action)
            .state((byte) 1)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .createdAt(convertedDatetime)
            .build();
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }

    void createTestDateToMasterSignal(UUID strategyId) {
        createMasterSignal(4, 3, 2, strategyId, tickerShare, tradingClearingAccountShare,
            "289.37", "10", 12);
        createMasterSignal(4, 2, 3, strategyId, tickerShare, tradingClearingAccountShare,
            "289.37", "10", 12);
        createMasterSignal(4, 1, 4, strategyId, tickerShare, tradingClearingAccountShare,
            "289.37", "10", 12);
        createMasterSignal(3, 7, 5, strategyId, tickerEtf, tradingClearingAccountEtf,
            "3310", "5", 12);
        createMasterSignal(3, 1, 6, strategyId, tickerBond, tradingClearingAccountBond,
            "106.663", "7", 12);
        createMasterSignal(2, 2, 7, strategyId, tickerMoney, tradingClearingAccountMoney,
            "70.8425", "2000", 12);
    }


}
