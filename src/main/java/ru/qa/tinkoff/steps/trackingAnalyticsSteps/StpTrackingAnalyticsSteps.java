package ru.qa.tinkoff.steps.trackingAnalyticsSteps;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingAnalyticsSteps {

    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;

    @Autowired(required = false)
    MasterPortfolioDao masterPortfolioDao;

    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategy;

    public String ticker1 = "SBER";
    public String tradingClearingAccount1 = "L01+00000F00";
    public String quantity1 = "50";
    public String sector1 = "financial";
    public String type1 = "share";
    public String company1 = "Сбер Банк";
    public String classCode1 = "TQBR";
    public String instrumet1 = ticker1 + "_" + classCode1;

    public String ticker2 = "SU29009RMFS6";
   public String tradingClearingAccount2 = "L01+00000F00";
   public String quantity2 = "3";
   public String classCode2 = "TQOB";
   public String sector2 = "government";
   public String type2 = "bond";
   public String company2 = "ОФЗ";
   public String instrumet2 = ticker2 + "_" + classCode2;
    //
    public String ticker3 = "LKOH";
    public String tradingClearingAccount3 = "L01+00000F00";
    public String quantity3 = "7";
    public String classCode3 = "TQBR";
    public String sector3 = "energy";
    public String type3 = "share";
    public String company3 = "Лукойл";
    public String instrumet3 = ticker3 + "_" + classCode3;

    public String ticker4 = "SNGSP";
    public String tradingClearingAccount4 = "L01+00000F00";
    public String quantity4 = "100";
    public String classCode4 = "TQBR";
    public String sector4 = "energy";
    public String type4 = "share";
    public String company4 = "Сургутнефтегаз";
    public String instrumet4 = ticker4 + "_" + classCode4;

    public String ticker5 = "TRNFP";
    public String tradingClearingAccount5 = "L01+00000F00";
    public String quantity5 = "4";
    public String classCode5 = "TQBR";
    public String sector5 = "energy";
    public String type5 = "share";
    public String company5 = "Транснефть";
    public String instrumet5 = ticker5 + "_" + classCode5;


    public String ticker6 = "ESGR";
    public String tradingClearingAccount6 = "L01+00000F00";
    public String quantity6 = "5";
    public String classCode6 = "TQTF";
    public String sector6 = "other";
    public String type6 = "etf";
    public String company6 = "РСХБ Управление Активами";
    public String instrumet6 = ticker6 + "_" + classCode6;

    public String ticker7 = "USD000UTSTOM";
    public String tradingClearingAccount7 = "MB9885503216";
    public String quantity7 = "1000";
    public String classCode7 = "CETS";
    public String sector7 = "money";
    public String type7 = "money";
    public String company7 = "Денежные средства";
    public String instrumet7 = ticker7 + "_" + classCode7;


    public String ticker8 = "YNDX";
    public String tradingClearingAccount8 = "L01+00000F00";
    public String quantity8 = "3";
    public String classCode8 = "TQBR";
    public String sector8 = "telecom";
    public String type8 = "share";
    public String company8= "Яндекс";
    public String instrumet8 = ticker8 + "_" + classCode8;


    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient.api(ru.qa.tinkoff.swagger.MD.invoker
        .ApiClient.Config.apiConfig()).prices();

//    public StpTrackingAnalyticsSteps() {
//    }


    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
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
            .setScore(1)
            .setFeeRate(feeRateProperties);
        strategy = trackingService.saveStrategy(strategy);
    }

    // создаем портфель ведущего с позициями в кассандре
    @Step("Создать договор и стратегию в бд автоследования для ведущего клиента {client}")
    @SneakyThrows
    public void createMasterPortfolioOnePosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());

        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(1)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }

    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioTwoPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());

        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());

        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }

    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioWithOutPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }



    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioThreePosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());

        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }

    // создаем портфель ведущего с позициями в кассандре
   public void createMasterPortfolioFourPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker4)
            .tradingClearingAccount(tradingClearingAccount4)
            .quantity(new BigDecimal(quantity4))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }

    // создаем портфель ведущего с позициями в кассандре
   public void createMasterPortfolioFivePosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker4)
            .tradingClearingAccount(tradingClearingAccount4)
            .quantity(new BigDecimal(quantity4))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker5)
            .tradingClearingAccount(tradingClearingAccount5)
            .quantity(new BigDecimal(quantity5))
            .changedAt(date)
            .lastChangeDetectedVersion(5)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }

    // создаем портфель ведущего с позициями в кассандре
   public void createMasterPortfolioSixPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker4)
            .tradingClearingAccount(tradingClearingAccount4)
            .quantity(new BigDecimal(quantity4))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker5)
            .tradingClearingAccount(tradingClearingAccount5)
            .quantity(new BigDecimal(quantity5))
            .changedAt(date)
            .lastChangeDetectedVersion(5)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker6)
            .tradingClearingAccount(tradingClearingAccount6)
            .quantity(new BigDecimal(quantity6))
            .changedAt(date)
            .lastChangeDetectedVersion(6)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }


    // создаем портфель ведущего с позициями в кассандре
   public void createMasterPortfolioSevenPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker4)
            .tradingClearingAccount(tradingClearingAccount4)
            .quantity(new BigDecimal(quantity4))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker5)
            .tradingClearingAccount(tradingClearingAccount5)
            .quantity(new BigDecimal(quantity5))
            .changedAt(date)
            .lastChangeDetectedVersion(5)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker6)
            .tradingClearingAccount(tradingClearingAccount6)
            .quantity(new BigDecimal(quantity6))
            .changedAt(date)
            .lastChangeDetectedVersion(6)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker7)
            .tradingClearingAccount(tradingClearingAccount7)
            .quantity(new BigDecimal(quantity7))
            .changedAt(date)
            .lastChangeDetectedVersion(7)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }




    //создаем портфель master в cassandra
   public void createMasterPortfolioWithChangedAt(String contractIdMaster, UUID strategyId, List<MasterPortfolio.Position> positionList, int version, String money, Date date) {
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, date, positionList);
    }



    //создаем команду в формате Protobuf в соответствии со схемой tracking.proto (message AnalyticsCommand)
    public Tracking.AnalyticsCommand createCommandAnalytics(OffsetDateTime createTime, OffsetDateTime cutTime,
                                                                         Tracking.AnalyticsCommand.Operation operation,
                                                                                Tracking.AnalyticsCommand.Calculation calculation,
                                                                         ByteString strategyId ) {
        Tracking.AnalyticsCommand command  = Tracking.AnalyticsCommand.newBuilder()
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(createTime.toEpochSecond())
                .setNanos(createTime.getNano())
                .build())
            .setOperation(operation)
            .setCalculation(calculation)
            .setStrategyId(strategyId)
            .setCut(Timestamp.newBuilder()
                .setSeconds(cutTime.toEpochSecond())
                .setNanos(cutTime.getNano())
                .build())
            .build();
        return command;
    }



    // получаем данные от ценах от MarketData
    public Map<String, BigDecimal> getPriceFromMarketAllDataWithDate(String ListInst, String type, String date, int size) {
        Response res = pricesApi.mdInstrumentsPrices()
            .instrumentsIdsQuery(ListInst)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .tradeTsQuery(date)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);

        Map<String, BigDecimal> pricesPos = new HashMap<>();
        for (int i = 0; i < size; i++) {
            pricesPos.put(res.getBody().jsonPath().getString("instrument_id[" + i + "]"),
                new BigDecimal(res.getBody().jsonPath().getString("prices[" + i + "].price_value[0]")));
        }
        return pricesPos;
    }

    public BigDecimal getValuePortfolio(Map<String, BigDecimal> pricesPos, String nominal,
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
            if (pair.getKey().equals(instrumet1)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrumet2)) {
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
                valuePos2 = new BigDecimal(quantity2).multiply(price);
            }
            if (pair.getKey().equals(instrumet3)) {
                valuePos3 = new BigDecimal(quantity3).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrumet4)) {
                valuePos4 = new BigDecimal(quantity4).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrumet5)) {
                valuePos5 = new BigDecimal(quantity5).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrumet6)) {
                valuePos6 = new BigDecimal(quantity6).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrumet7)) {
                valuePos7 = new BigDecimal(quantity7).multiply((BigDecimal) pair.getValue());
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


    public BigDecimal valuePosBonds(String priceTs, String nominal,BigDecimal minPriceIncrement, String aciValue, BigDecimal valuePos) {
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price =roundPrice
            .add(new BigDecimal(aciValue));
        valuePos = new BigDecimal(quantity2).multiply(price);
        return valuePos;
    }


    // получаем данные от ценах от MarketData
    public String getPriceFromMarketDataWithDate(String instrumentId, String type, String date) {
        Response res = pricesApi.mdInstrumentsPrices()
            .instrumentsIdsQuery(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .tradeTsQuery(date)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0][0]");
        return price;
    }


    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }


    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }






}
