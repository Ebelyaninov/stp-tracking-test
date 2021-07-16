package stpTrackingSlave.handleActualizeCommand;


import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию")
@Feature("TAP-6864")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    GrpcServicesAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class HandleActualizeCommandTest {
    @Autowired
    MiddleGrpcService middleGrpcService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
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
    StpTrackingSlaveSteps steps;


    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Contract contract;
    Client clientSlave;
    String contractIdMaster;
    String ticker = "AAPL";
    String tradingClearingAccount = "L01+00000SPB";
    String classCode = "SPBXM";
    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-AJ7L9FNI";
    String SIEBEL_ID_SLAVE = "5-JEF71TBN";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(steps.clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientSlave);
            } catch (Exception e) {
            }
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("731513")
    @DisplayName("C731513.HandleActualizeCommand.Инициализация slave-портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731513() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
//        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 2, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePositionRate = BigDecimal.ZERO;
        BigDecimal quantityDiff = (masterPositionRate.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем значение портфеля slave
        checkSlavePortfolioParameters(2, 3, "7000");
        assertThat("Время changed_at для base_money_position не равно", slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        checkPositionParameters(0, ticker, tradingClearingAccount, "0", price, slavePositionRate, masterPositionRate,
            quantityDiff, null);
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(2, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
    }


    @SneakyThrows
    @Test
    @AllureId("741543")
    @DisplayName("C741543.HandleActualizeCommand.Инициализация slave-портфеля c позицией")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C741543() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
//        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = new BigDecimal("2").multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем значение портфеля slave
        checkSlavePortfolioParameters(2, 3, "7000");
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "39");
        assertThat("changed_at позиции в портфеле slave не равен",
            slavePortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(2, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
    }


    @SneakyThrows
    @Test
    @AllureId("748732")
    @DisplayName("C748732.HandleActualizeCommand.Инициализация slave-портфеля, не передан параметр base_money_position")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C748732() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
//        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 3,
            steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = new BigDecimal("2").multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем значение портфеля slave
        checkSlavePortfolioParameters(3, 3, "0");
        assertThat("changed_at для base_money_position в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(), is(nullValue()));
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "39");
        assertThat("changed_at позиции в портфеле slave не равен",
            slavePortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("1053004")
    @DisplayName("1053004.HandleActualizeCommand.Получение измененных позиций из Middle.Cохранение в кэш actualizeCommandCache")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля= 1," +
        " Action = 'MORNING_UPDATE',version из команды < version из ответа ")
    void C1053004() {
        String SIEBEL_ID_SLAVE = "1-1IE1IUG";
        String ticker = "YNDX";
        String tradingClearingAccount = "Y02+00001F00";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
//        steps.createDataToMarketData(ticker, "TQBR", "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle - 1,
            steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.MORNING_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);


//        //получаем содержимое кеша exchangePositionPriceCache по певой ноде
//        Response resCacheNodeZero = cacheApi.getAllEntities()
//            .reqSpec(r -> r.addHeader("api-key", "tracking"))
//            .reqSpec(r -> r.addHeader("magic-number", "3"))
//            .cacheNamePath("actualizeCommandCache")
//            .xAppNameHeader("tracking")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response);

        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle - 2));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySl));
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));

    }


    @SneakyThrows
    @Test
    @AllureId("1054936")
    @DisplayName("1054936.HandleActualizeCommand.Все изменения отражены в команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    void C1054936() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 588486,
            contractIdSlave, versionMiddle, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        checkSlavePortfolioParameters(versionMiddle, 3, "5884.86");
        assertThat("lastChangeAction BaseMoney не равно", slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 12));
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("5"));
        assertThat("lastChangeAction Position не равно", slavePortfolio.getPositions().get(0).getLastChangeAction(), is((byte) 12));
    }


    @SneakyThrows
    @Test
    @AllureId("1057608")
    @DisplayName("1057608.HandleActualizeCommand.Команда по отложенным изменениям - параметр delayed_correction = true")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля  > 1, delayed_correction = true")
    void C1057608() {
        String SIEBEL_ID_SLAVE = "1-1IE1IUG";
        String ticker = "YNDX";
        String tradingClearingAccount = "Y02+00001F00";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //получаем базовую валюту
        double middleQuantityBaseMoney = getBaseMoneyFromMiddle(clientPositions, "RUB");
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle - 1,
            steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.MORNING_UPDATE), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle - 2));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySl));
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
        //формируем новую команду на актуализацию для slave
        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyBaseMoney(2, 588486, contractIdSlave,
            versionMiddle, time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, true);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(middleQuantityBaseMoney));
        assertThat("Version в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is("USD000UTSTOM"));
    }




    @SneakyThrows
    @Test
    @AllureId("1062109")
    @DisplayName("C1062109.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если позиция и базовая валюта изменились по команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1062109() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        String ticker2 = "ABBV";
        String classCode2 = "SPBXM";
        String tradingClearingAccount2 = "L01+00000SPB";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        steps.createDataToMarketData(ticker2, classCode2, "90", "90", "87");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster,
            null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", ticker2, tradingClearingAccount2, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", ticker2, tradingClearingAccount2, "1", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(4));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 12));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            //если это позиция, по которой пришла актуализация по команде, проверяем изменения
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
                assertThat("quantity позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getQuantity().toString(), is("5"));
                assertThat("lastChangeAction позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getLastChangeAction(), is((byte) 12));
            }
            //если это позиция, без изменений
            if (ticker2.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
                assertThat("quantity позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getQuantity().toString(), is("1"));
                assertThat("lastChangeAction позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getLastChangeAction(), is(nullValue()));
            }
        }
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1063048")
    @DisplayName("C1063048.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если базовая валюта не изменились по команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1063048() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        String ticker2 = "ABBV";
        String classCode2 = "SPBXM";
        String tradingClearingAccount2 = "L01+00000SPB";
        BigDecimal lot = new BigDecimal("1");
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        steps.createDataToMarketData(ticker2, classCode2, "90", "90", "87");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster,
            null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", ticker2, tradingClearingAccount2, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", ticker2, tradingClearingAccount2, "1", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave,
            4, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(4));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6551.10"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }

//
//    @SneakyThrows
//    @Test
//    @AllureId("1065723")
//    @DisplayName("C1065723.HandleActualizeCommand.Формирование актуального набора позиций, " +
//        "если узнали об изменении позиции и базовой валюте из gRPC middle")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
//    void C1065723() {
//        String SIEBEL_ID_SLAVE = "5-167ET5VFO";
//        String title = "тест стратегия autotest update base currency";
//        String description = "description test стратегия autotest update adjust base currency";
//        String ticker2 = "FB";
//        String classCode2 = "SPBXM";
//        String tradingClearingAccount2 = "L01+00000SPB";
//        BigDecimal lot = new BigDecimal("1");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту slave в БД сервиса счетов
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//       contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        strategyId = UUID.randomUUID();
////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster,
//            null, ContractState.untracked, strategyId, title, description,
//            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker2, tradingClearingAccount2,
//            "20", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
//        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
//        //создаем подписку на стратегию для slave
//        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
//        int versionMiddle = 15;
//        // создаем портфель slave с позицией в кассандре
//        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker2, tradingClearingAccount2,
//            "10", date);
//        String baseMoneySl = "3000.0";
//        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
//            baseMoneySl, date, createListSlaveOnePos);
//        OffsetDateTime time = OffsetDateTime.now();
//        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
//            versionMiddle - 1, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
//                Tracking.Portfolio.Action.MORNING_UPDATE), time, Tracking.Portfolio.Action.MORNING_UPDATE, false);
//        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
//        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
//        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle - 2));
//        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySl));
//        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
//        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
//        //формируем новую команду на актуализацию для slave
//        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
//            steps.createPosInCommand(ticker2, tradingClearingAccount2, 20, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
//            time, true);
//
//        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
//        checkComparedSlaveVersion(versionMiddle);
//        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
//        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
//
//
//    }



    @SneakyThrows
    @Test
    @AllureId("742580")
    @DisplayName("C742580.HandleActualizeCommand.Актуализация портфеля, без выставления заявки")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742580() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        String ticker2 = "ABBV";
        String classCode2 = "SPBXM";
        String tradingClearingAccount2 = "L01+00000SPB";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        steps.createDataToMarketData(ticker2, classCode2, "90", "90", "87");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster,
            null, ContractState.untracked, strategyId, title, description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", ticker2, tradingClearingAccount2, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(ticker, tradingClearingAccount,
            "2", ticker2, tradingClearingAccount2, "1", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        checkSlavePortfolioParameters(4, 4, "5855.6");
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getQuantityDiff().toString(), is("0"));
//        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(1).getQuantityDiff().toString(), is("0"));
        //проверяем значение changed_at у позиции
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            }
            if (ticker2.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            }
        }
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }



    @SneakyThrows
    @Test
    @AllureId("731504")
    @DisplayName("C731504.HandleActualizeCommand.Получение подтверждения в полном объеме")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731504() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 3,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "5855.6");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff, "12");
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("856826")
    @DisplayName("C856826.HandleActualizeCommand.Подтверждение по отклоненной заявке, полный объем заявки еще не подтвержден")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C856826() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster,null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        BigDecimal positionQuantityCommand = new BigDecimal("2");
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 2,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "5855.6");
        checkPositionParameters(0, ticker, tradingClearingAccount, "2", price, slavePositionRate, rateDiff,
            quantityDiff, "12");
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantityCommand.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("State не равно", slaveOrder.getState(), is(nullValue()));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
    }


    @SneakyThrows
    @Test
    @AllureId("1071599")
    @DisplayName("C1071599.HandleActualizeCommand.Получение подтверждения в полном объеме по одной позиции, " +
        "выставление новой заявки по другой позиции")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1071599() {
        String ticker2 = "ABBV";
        String classCode2 = "SPBXM";
        String tradingClearingAccount2 = "L01+00000SPB";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        steps.createDataToMarketData(ticker2, classCode2, "90", "90", "87");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", ticker2, tradingClearingAccount2, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 3,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal QuantityDiffticker = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(3, 4, "6855.6");
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker.toString(), is("0"));
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1"));
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 3, Byte.valueOf("1"));
        assertThat("State не равно", slaveOrder.getQuantity().toString(), is("1"));
        assertThat("State не равно", slaveOrder.getTicker(), is(ticker2));
    }


    @SneakyThrows
    @Test
    @AllureId("1071663")
    @DisplayName("C1071663.HandleActualizeCommand.Получение подтверждения в полном объеме по одной позиции, blocked != false")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1071663() {
        String ticker2 = "ABBV";
        String classCode2 = "SPBXM";
        String tradingClearingAccount2 = "L01+00000SPB";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        steps.createDataToMarketData(ticker2, classCode2, "90", "90", "87");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", ticker2, tradingClearingAccount2, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 3,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, ticker, tradingClearingAccount);
        contract = contractService.updateBlockedContract(contractIdSlave, true);
        steps.createEventInTrackingEventWithBlock(contractIdSlave, true);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            3, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal QuantityDiffticker = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(3, 4, "6855.6");
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker.toString(), is("0"));
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrderWithVersionAndAttemps(contractIdSlave, strategyId, 2, Byte.valueOf("1"));
        assertThat("State не равно", slaveOrder.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder.getFilledQuantity(), is(updatedFilledQuanitity));
        //смотрим, что заявка не выставлялась
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrderWithVersionAndAttemps(contractIdMaster, strategyId, 3, Byte.valueOf("1"));
        assertThat("запись по заявке не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("742614")
    @DisplayName("C742614.HandleActualizeCommand.Синхронизируем портфель, после актуализации")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742614() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        String ticker2 = "ABBV";
        String classCode2 = "SPBXM";
        String tradingClearingAccount2 = "L01+00000SPB";
        BigDecimal lot = new BigDecimal("1");
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        steps.createDataToMarketData(ticker2, classCode2, "90", "90", "87");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", ticker2, tradingClearingAccount2, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(ticker, tradingClearingAccount,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(ticker2, tradingClearingAccount2, 1,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal QuantityDiffticker1 = BigDecimal.ZERO;
        BigDecimal QuantityDiffticker2 = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (ticker.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker1 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
            if (ticker2.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker2 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(4, 4, "5855.6");
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker1.toString(), is("1"));
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker2.toString(), is("0"));
        // рассчитываем значение
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal lots = QuantityDiffticker1.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(4, "0", lot, lots, priceOrder, ticker, tradingClearingAccount, classCode);
    }


    @SneakyThrows
    @Test
    @AllureId("742634")
    @DisplayName("C742634.HandleActualizeCommand.Ожидаем подтверждение дальше," +
        " position.action NOT IN ('SECURITY_BUY_TRADE', 'SECURITY_SELL_TRADE')")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742634() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster,null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 2, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 3,
            steps.createPosInCommand(ticker, tradingClearingAccount, 5,
            Tracking.Portfolio.Action.COUPON_TAX), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(3, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff, "19");
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("найдена запись в masterPortfolio", slaveOrder.getState(), is(nullValue()));
    }


    // методы для работы тестов*************************************************************************

    Tracking.PortfolioCommand createCommandActualizeOnlyBaseMoney(int scale, int unscaled, String contractIdSlave,
                                                                    int version, OffsetDateTime time,
                                                                    Tracking.Portfolio.Action action, boolean delayedCorrection) {
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }


    Tracking.PortfolioCommand createCommandActualizeWithPosition(int scale, int unscaled, String contractIdSlave,
                                                                    int version, Tracking.Portfolio.Position position,
                                                                    OffsetDateTime time, Tracking.Portfolio.Action action,
                                                                    boolean delayedCorrection) {
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())

            .build();
        return command;
    }


    Tracking.PortfolioCommand createCommandActualizeOnlyPosition(String contractIdSlave, int version,
                                                                   Tracking.Portfolio.Position position,
                                                                   OffsetDateTime time, boolean delayedCorrection) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }


    public void checkSlavePortfolioParameters(int version, int comparedToMasterVersion, String baseMoney) {
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(version));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(comparedToMasterVersion));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney));
    }


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal rateDiff, BigDecimal quantityDiff, String lastChangeAction) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        slavePortfolio.getPositions().get(pos).getRate().compareTo(slavePositionRate);
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
        Byte value = lastChangeAction == null ? null : Byte.valueOf(lastChangeAction);
        assertThat("lastChangeAction позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getLastChangeAction(), is(value));
    }


    public void checkOrderParameters(int version, String action, BigDecimal lot, BigDecimal lots,
                                     BigDecimal priceOrder, String ticker, String tradingClearingAccount,
                                     String classCode) {
        assertThat("Версия портфеля не равно", slaveOrder.getVersion(), is(version));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getPrice(), is(priceOrder));
        assertThat("price бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder.getFilledQuantity(), is(new BigDecimal("0")));
    }


    void checkComparedSlaveVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(7000);
            } else {
                break;
            }
        }
    }


    double getBaseMoneyFromMiddle(CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions, String curBaseMoney) {
        double middleQuantityBaseMoney = 0;
        //складываем позиции по валютам у которых kind =365 в map, в отдельную map складем базовую валюту и ее значение
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getMoneyCount(); i++) {
            //значение по базовой валюте кладем в middleQuantityBaseMoney
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (curBaseMoney.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                middleQuantityBaseMoney = (clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getScale()))
                    + (clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getScale()));
            }

        }
        return middleQuantityBaseMoney;
    }


    Map<String, Double> createMapPostionFromMiddle(CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions, String currency,
                                                   String eur, String currencyTicker) {

        Map<String, Double> hashMapMiddlePos = new HashMap<>();
        //складываем позиции по валютам у которых kind =365 в map, в отдельную map складем базовую валюту и ее значение
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getMoneyCount(); i++) {
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (currency.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                double quantityCur = (clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getScale()))
                    + (clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getScale()));
                ;
                hashMapMiddlePos.put(currencyTicker, quantityCur);
            }

            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (eur.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                double quantityCur = (clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getScale()))
                    + (clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getScale()));

                hashMapMiddlePos.put("EUR_RUB__TOM", quantityCur);
            }
        }
        // складываем позиции по цб у которых kind =365 в map
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getSecuritiesCount(); i++) {
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getSecurities(i).getKind().name())) {
                double quantitySer = (clientPositions.getResponse().getClientPositions().getSecurities(i).getBalance().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getSecurities(i).getBalance().getScale()))
                    + (clientPositions.getResponse().getClientPositions().getSecurities(i).getBlocked().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getSecurities(i).getBlocked().getScale()));

                hashMapMiddlePos.put(clientPositions.getResponse().getClientPositions().getSecurities(i).getTicker(), quantitySer);
            }
        }
        return hashMapMiddlePos;
    }


    List<String> getTradingClearingAccountMiddlePos(CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions, String currency, String eur) {
        List<String> tradingClearingAccountMiddlePos = new ArrayList<String>();
        //складываем позиции по валютам у которых kind =365 в map, в отдельную map складем базовую валюту и ее значение
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getMoneyCount(); i++) {
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (currency.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                tradingClearingAccountMiddlePos.add(clientPositions.getResponse().getClientPositions().getMoney(i).getAccountId());
            }

            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (eur.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                tradingClearingAccountMiddlePos.add(clientPositions.getResponse().getClientPositions().getMoney(i).getAccountId());
            }
        }
        // складываем позиции по цб у которых kind =365 в map
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getSecuritiesCount(); i++) {
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getSecurities(i).getKind().name())) {
                tradingClearingAccountMiddlePos.add(clientPositions.getResponse().getClientPositions().getSecurities(i).getAccountId());
            }
        }
        return tradingClearingAccountMiddlePos;
    }
}
