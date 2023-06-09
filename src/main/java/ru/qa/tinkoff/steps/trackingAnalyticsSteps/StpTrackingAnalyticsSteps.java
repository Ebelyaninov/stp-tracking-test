package ru.qa.tinkoff.steps.trackingAnalyticsSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.creator.BrokerAccountApiCreator;
import ru.qa.tinkoff.creator.FiregInstrumentsApiCreator;
import ru.qa.tinkoff.creator.PricesMDApiCreator;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
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
    private final SubscriptionService subscriptionService;
    private final SlavePortfolioDao slavePortfolioDao;
    private final PricesMDApiCreator pricesMDApiCreator;
    private final BrokerAccountApiCreator brokerAccountApiCreator;
    private final FiregInstrumentsApiCreator firegInstrumentsApiCreator;
    private final StrategyService strategyService;

    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategy;
    public Subscription subscription;
    public Contract contractSlave;
    public Client clientSlave;
    public Client client;
    public Contract contract;


    @Autowired(required = false)
    MasterPortfolioDao masterPortfolioDao;
    @Autowired(required = false)
    StpInstrument instrument;


    public String quantitySBER = "50";
    public String quantitySBERZero = "0";
    public String quantitySU29009RMFS6 = "3";
    public String quantityLKOH = "7";
    public String quantitySNGSP = "100";
    public String quantityTRNFP = "4";
    public String quantityESGR = "5";
    public String quantityESGRZero = "-5";
    public String quantityUSD = "1000";
    public String quantityYNDX = "3";

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, LocalDateTime closeTime) {
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy());
        List<TestsStrategy> tagsStrategiesList = new ArrayList<>();
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
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setCloseTime(closeTime)
            .setTags(tagsStrategiesList);
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());

        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }


    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioTwoPositionWithZero(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());

        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBERZero))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerLKOH)
            .tradingClearingAccount(instrument.tradingClearingAccountLKOH)
            .quantity(new BigDecimal(quantityLKOH))
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerLKOH)
            .tradingClearingAccount(instrument.tradingClearingAccountLKOH)
            .quantity(new BigDecimal(quantityLKOH))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSNGSP)
            .tradingClearingAccount(instrument.tradingClearingAccountSNGSP)
            .quantity(new BigDecimal(quantitySNGSP))
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerLKOH)
            .tradingClearingAccount(instrument.tradingClearingAccountLKOH)
            .quantity(new BigDecimal(quantityLKOH))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSNGSP)
            .tradingClearingAccount(instrument.tradingClearingAccountSNGSP)
            .quantity(new BigDecimal(quantitySNGSP))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerTRNFP)
            .tradingClearingAccount(instrument.tradingClearingAccountTRNFP)
            .quantity(new BigDecimal(quantityTRNFP))
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerLKOH)
            .tradingClearingAccount(instrument.tradingClearingAccountLKOH)
            .quantity(new BigDecimal(quantityLKOH))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSNGSP)
            .tradingClearingAccount(instrument.tradingClearingAccountSNGSP)
            .quantity(new BigDecimal(quantitySNGSP))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerTRNFP)
            .tradingClearingAccount(instrument.tradingClearingAccountTRNFP)
            .quantity(new BigDecimal(quantityTRNFP))
            .changedAt(date)
            .lastChangeDetectedVersion(5)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerESGR)
            .tradingClearingAccount(instrument.tradingClearingAccountESGR)
            .quantity(new BigDecimal(quantityESGR))
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
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerLKOH)
            .tradingClearingAccount(instrument.tradingClearingAccountLKOH)
            .quantity(new BigDecimal(quantityLKOH))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSNGSP)
            .tradingClearingAccount(instrument.tradingClearingAccountSNGSP)
            .quantity(new BigDecimal(quantitySNGSP))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerTRNFP)
            .tradingClearingAccount(instrument.tradingClearingAccountTRNFP)
            .quantity(new BigDecimal(quantityTRNFP))
            .changedAt(date)
            .lastChangeDetectedVersion(5)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerESGR)
            .tradingClearingAccount(instrument.tradingClearingAccountESGR)
            .quantity(new BigDecimal(quantityESGR))
            .changedAt(date)
            .lastChangeDetectedVersion(6)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerUSD)
            .tradingClearingAccount(instrument.tradingClearingAccountUSD)
            .quantity(new BigDecimal(quantityUSD))
            .changedAt(date)
            .lastChangeDetectedVersion(7)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }



    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioSevenPositionWithZero(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBERZero))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerLKOH)
            .tradingClearingAccount(instrument.tradingClearingAccountLKOH)
            .quantity(new BigDecimal(quantityLKOH))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSNGSP)
            .tradingClearingAccount(instrument.tradingClearingAccountSNGSP)
            .quantity(new BigDecimal(quantitySNGSP))
            .changedAt(date)
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerTRNFP)
            .tradingClearingAccount(instrument.tradingClearingAccountTRNFP)
            .quantity(new BigDecimal(quantityTRNFP))
            .changedAt(date)
            .lastChangeDetectedVersion(5)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerESGR)
            .tradingClearingAccount(instrument.tradingClearingAccountESGR)
            .quantity(new BigDecimal(quantityESGRZero))
            .changedAt(date)
            .lastChangeDetectedVersion(6)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerUSD)
            .tradingClearingAccount(instrument.tradingClearingAccountUSD)
            .quantity(new BigDecimal(quantityUSD))
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
    public Map<String, BigDecimal> getPriceFromMarketAllDataWithDate(List<String> instrumentList, String type, String date, int size) {

        Map<String, BigDecimal> pricesPos2 = new HashMap<>();
        for (int i = 0; i < size; i++) {
            Response res2 = pricesMDApiCreator.get().mdInstrumentPrices()
                .instrumentIdPath(instrumentList.get(i))
                .typesQuery(type)
                .tradeTsQuery(date)
                .systemCodeQuery("111")
                .requestIdQuery("111")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response);

            pricesPos2.put(res2.getBody().jsonPath().getString("instrument_id"),
                new BigDecimal(res2.getBody().jsonPath().getString("prices[" + 0 + "].price_value")));
        }
        return pricesPos2;
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
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(quantitySBER).multiply((BigDecimal) pair.getValue());
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
                BigDecimal price =roundPrice
                    .add(new BigDecimal(aciValue));
                valuePos2 = new BigDecimal(quantitySU29009RMFS6).multiply(price);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSNGSP)) {
                valuePos4 = new BigDecimal(quantitySNGSP).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentTRNFP)) {
                valuePos5 = new BigDecimal(quantityTRNFP).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentESGR)) {
                valuePos6 = new BigDecimal(quantityESGR).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentUSD)) {
                valuePos7 = new BigDecimal(quantityUSD).multiply((BigDecimal) pair.getValue());
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



    public BigDecimal getValuePortfolioWithZero(Map<String, BigDecimal> pricesPos, String nominal,
                                        BigDecimal minPriceIncrement, String aciValue,String baseMoney) {

        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal valuePos3 = BigDecimal.ZERO;
        BigDecimal valuePos4 = BigDecimal.ZERO;
        BigDecimal valuePos5 = BigDecimal.ZERO;

        BigDecimal valuePos7 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
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
                valuePos2 = new BigDecimal(quantitySU29009RMFS6).multiply(price);
            }
            if (pair.getKey().equals(instrument.instrumentLKOH)) {
                valuePos3 = new BigDecimal(quantityLKOH).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentSNGSP)) {
                valuePos4 = new BigDecimal(quantitySNGSP).multiply((BigDecimal) pair.getValue());
            }
            if (pair.getKey().equals(instrument.instrumentTRNFP)) {
                valuePos5 = new BigDecimal(quantityTRNFP).multiply((BigDecimal) pair.getValue());
            }

            if (pair.getKey().equals(instrument.instrumentUSD)) {
                valuePos7 = new BigDecimal(quantityUSD).multiply((BigDecimal) pair.getValue());
            }
        }
        BigDecimal valuePortfolio = valuePos2
            .add(valuePos3)
            .add(valuePos4)
            .add(valuePos5)
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
        valuePos = new BigDecimal(quantitySU29009RMFS6).multiply(price);
        return valuePos;
    }


    // получаем данные от ценах от MarketData
    public String getPriceFromMarketDataWithDate(String instrumentId, String type, String date) {
        Response res = pricesMDApiCreator.get().mdInstrumentPrices()
            .instrumentIdPath(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .tradeTsQuery(date)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices[" + 0 + "].price_value");
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


    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionWithBlocked(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                             java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, null);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        if(contractState.equals(ContractState.untracked)){
            contractSlave.setStrategyId(null);
        }
        contractSlave = contractService.saveContract(contractSlave);
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);
    }


    public void createSlavePortfolioWithPosition(String contractIdSlave, UUID strategyId, int version, int comparedToMasterVersion,
                                                 String money,Date date, List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .lastChangeAction(null)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList, date);
    }

    public List<SlavePortfolio.Position> createListSlavePositionWithOnePosLight(String ticker, String tradingClearingAccount,
                                                                                String quantityPos, Date date)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        return positionList;
    }

    public List<SlavePortfolio.Position> createListSlavePositionWithOnePos(String ticker, String tradingClearingAccount,
                                                                                String quantityPos, Date date, BigDecimal price,
                                                                           BigDecimal quantityDiff)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .changedAt(date)
            .price(price)
            .quantityDiff(quantityDiff)
            .lastChangeAction(null)
            .build());
        return positionList;
    }



    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPosLight(String ticker1, String tradingClearingAccount1,
                                                                                String quantityPos1, Date date1, String ticker2, String tradingClearingAccount2,
                                                                                String quantityPos2, Date date2)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date1)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date2)
            .lastChangeAction(null)
            .build());
        return positionList;
    }


    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPos(String ticker1, String tradingClearingAccount1,
                                                                                String quantityPos1, Date date1, String ticker2, String tradingClearingAccount2,
                                                                                String quantityPos2, Date date2, BigDecimal price1, BigDecimal price2,
                                                                           BigDecimal quantityDiff1, BigDecimal quantityDiff2)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date1)
            .price(price1)
            .quantityDiff(quantityDiff1)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date2)
            .price(price2)
            .quantityDiff(quantityDiff2)
            .lastChangeAction(null)
            .build());
        return positionList;

    }


    public List<SlavePortfolio.Position> createListSlavePositionWithThreePosLight(String ticker1, String tradingClearingAccount1,
                                                                                  String quantityPos1, Date date1, String ticker2, String tradingClearingAccount2,
                                                                                  String quantityPos2, Date date2, String ticker3, String tradingClearingAccount3,
                                                                                  String quantityPos3, Date date3)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date1)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date2)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantityPos3))
            .changedAt(date3)
            .lastChangeAction(null)
            .build());
        return positionList;
    }


    public List<SlavePortfolio.Position> createListSlavePositionWithFourPosLight(String ticker1, String tradingClearingAccount1,
                                                                                  String quantityPos1, Date date1, String ticker2, String tradingClearingAccount2,
                                                                                  String quantityPos2, Date date2, String ticker3, String tradingClearingAccount3,
                                                                                  String quantityPos3, Date date3, String ticker4, String tradingClearingAccount4,
                                                                                 String quantityPos4, Date date4)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date1)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date2)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantityPos3))
            .changedAt(date3)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker4)
            .tradingClearingAccount(tradingClearingAccount4)
            .quantity(new BigDecimal(quantityPos4))
            .changedAt(date4)
            .lastChangeAction(null)
            .build());
        return positionList;
    }


    public List<SlavePortfolio.Position> createListSlavePositionWithThreePos(String ticker1, String tradingClearingAccount1,
                                                                                  String quantityPos1, Date date1, String ticker2, String tradingClearingAccount2,
                                                                                  String quantityPos2, Date date2, String ticker3, String tradingClearingAccount3,
                                                                                  String quantityPos3, Date date3, BigDecimal price1, BigDecimal price2, BigDecimal price3,
                                                                             BigDecimal quantityDiff1, BigDecimal quantityDiff2, BigDecimal quantityDiff3)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date1)
            .price(price1)
            .quantityDiff(quantityDiff1)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date2)
            .price(price2)
            .quantityDiff(quantityDiff2)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantityPos3))
            .changedAt(date3)
            .price(price3)
            .quantityDiff(quantityDiff3)
            .lastChangeAction(null)
            .build());
        return positionList;
    }




    public String getTitleStrategy(){
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" + randomNumber;
        return title;
    }



    public List<String> getDateBondFromInstrument (String ticker, String classCode, String dateFireg) {
        List<String> dateBond = new ArrayList<>();
        Response resp = firegInstrumentsApiCreator.get().instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker)
            .idKindQuery("ticker")
            .classCodeQuery(classCode)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        dateBond.add(aciValue);
        dateBond.add(nominal);
        return dateBond;
    }

    @Step("Удаляем записи из strategy + contract + client")
    public void deleteDataFromDb (String contractId, UUID clientId) {
        try {
            strategyService.deleteStrategy(strategyService.findStrategyByContractId(contractId).get());
        } catch (Exception e) {}
        try {
            contractService.deleteContract(contractService.getContract(contractId));
        } catch (Exception e) {}
        try {
            clientService.deleteClient(clientService.getClient(clientId));
        } catch (Exception e) {}
    }

}
