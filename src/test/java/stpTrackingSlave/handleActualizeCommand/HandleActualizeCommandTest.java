package stpTrackingSlave.handleActualizeCommand;

import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
import org.springframework.test.annotation.Repeat;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.mocks.steps.MocksBasicSteps;
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMockSlaveDateConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMockSlave.StpMockSlaveDate;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию")
@Feature("TAP-6864")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleActualizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    GrpcServicesAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    MocksBasicStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingMockSlaveDateConfiguration.class
})
public class HandleActualizeCommandTest {
    @Autowired
    MiddleGrpcService middleGrpcService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    SlaveOrder2Dao slaveOrder2Dao;
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
    @Autowired
    MocksBasicSteps mocksBasicSteps;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpMockSlaveDate stpMockSlaveDate;

    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    SlaveOrder2 slaveOrder2;
    SlaveOrder2 slaveOrderNew;
    Contract contract;
    Client clientSlave;
    String contractIdMaster;
    Subscription subscription;
    String contractIdSlave;
    UUID strategyId;
    UUID strategyIdNew;
    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;

    public String value;

    String description = "description test стратегия autotest update adjust base currency";

    @BeforeAll void createDataForTests() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdSlaveMaster;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdSlaveSlave;
        mocksBasicSteps.createDataForMasterMock(SIEBEL_ID_MASTER);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                subscriptionService.deleteSubscription(subscription);
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyIdNew);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdMaster);
            } catch (Exception e) {
            }
            try {
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    //д.б. USD= 7000 в мидл
    @SneakyThrows
    @Test
    @AllureId("731513")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C731513.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля с базовой валютой")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731513() {
        String SIEBEL_ID_SLAVE = "1-FRT3HXX";
        BigDecimal lot = new BigDecimal("1");
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "7000", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        strategyIdNew = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, versionMiddle - 2, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);

        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        // рассчитываем значение lots
        BigDecimal lots = slavePortfolio.getPositions().get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder2
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        checkOrderParameters(versionMiddle - 2, 3,"0", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.classCodeAAPL);
        steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
    }

    //пустой список позиций 0 - по деньгам в мидл
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1366344")
    @DisplayName("C1366344.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация пустого slave-портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1366344() {
        String SIEBEL_ID_SLAVE = "5-3NRSEZFX";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(versionMiddle)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                        .build())
                    .build())
                .setDelayedCorrection(false)
                .build())
            .build();
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
    }


    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("741543")
    @DisplayName("C741543.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля c позицией")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C741543() {
        String SIEBEL_ID_SLAVE = "5-HWHTMYS3";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "7000", "0", "2");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkSlavePortfolioParameters(versionMiddle, 2, "7000");
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "null");
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        checkOrderParameters(versionMiddle, 2,"0", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.classCodeAAPL);
    }

    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1416943")
    @DisplayName("C1416943.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля c пустой позицией")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1416943() {
        String SIEBEL_ID_SLAVE = "1-38B7AFZ";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "7000", "0", "2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMinutes(7));
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(7);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMinutes(5);
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();

        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerAAPL)
            .setTradingClearingAccount(instrument.tradingClearingAccountAAPL)
            .setQuantity(Tracking.Decimal.newBuilder().build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                .build())
            .build();
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(0)
            .setUnscaled(7000)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(versionMiddle)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                        .build())
                    .build())
                .addPosition(position)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.TRACKING_STATE_UPDATE)
                        .build())
                    .build())
                .setDelayedCorrection(false)
                .build())
            .build();
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(600)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkSlavePortfolioParameters(versionMiddle, 2, "7000");
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "39");
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(true));
        assertThat("Id стратегии в заявке не равно", order.get().getStrategyId(), is(strategyId));
        assertThat("время выставления в заявке не равно", order.get().getCreateAt().toInstant().truncatedTo(ChronoUnit.MINUTES),
            is(time.toInstant().truncatedTo(ChronoUnit.MINUTES)));
    }


    // д.б. AAPL=2 USD=0
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("748732")
    @DisplayName("C748732.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля, не передан параметр base_money_position")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C748732() {
        String SIEBEL_ID_SLAVE = "5-7OOOE6B1";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkSlavePortfolioParameters(versionMiddle, 3, "0");
        assertThat("changed_at для base_money_position в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(), is(nullValue()));
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "39");
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1053004")
    @DisplayName("1053004.HandleActualizeCommand.Обрабатываем версии.Version из команды - slave_portfolio.version текущего портфеля= 1" +
        "Action = 'MORNING_UPDATE',version из команды < version из ответа")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля= 1," +
        " Action = 'MORNING_UPDATE',version из команды < version из ответа ")
    void C1053004() {
        String SIEBEL_ID_SLAVE = "5-22NVD3I1";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle - 1,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, Tracking.Portfolio.Action.MORNING_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle - 1);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //проверяем, что заявка не выставлялась
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1054936")
    @DisplayName("1054936.HandleActualizeCommand.Все изменения отражены в команде." +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    void C1054936() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "3");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 588486,
            contractIdSlave, versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        checkSlavePortfolioParameters(versionMiddle, 3, "5884.86");
        assertThat("lastChangeAction BaseMoney не равно", slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 12));
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("5"));
        assertThat("lastChangeAction Position не равно", slavePortfolio.getPositions().get(0).getLastChangeAction(), is((byte) 12));
    }


    //должно быть 4 яндексов по позициям в мидле
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1057608")
    @DisplayName("1057608.HandleActualizeCommand.Version из команды - slave_portfolio.version текущего портфеля  > 1." +
        "Получаем актуальный портфель из Middle.Version из команды < version из ответа.Cохранение в кэш actualizeCommandCache")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля  > 1, Version из команды < version из ответа")
    void C1057608() {
        String SIEBEL_ID_SLAVE = "5-3HYUEXL7";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerYNDX, instrument.classCodeYNDX, instrument.tradingClearingAccountYNDX,
            "3000", "0", "0", "4");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //получаем базовую валюту
        double middleQuantityBaseMoney = getBaseMoneyFromMiddle(clientPositions, "RUB");
        // создаем портфель slave с позицией в кассандре и версией портфеля меньше чем в middle на 3
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 3, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave, с версией меньше чем в middle на 1
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle - 1,
            steps.createPosInCommand(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, 2, Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //проверяем, что портфель не создан по условию version из команды < version из ответа, то сохраняем в кэш actualizeCommandCache
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle - 1);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //проверяем, что заявка не выставлялась
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
        //формируем новую команду на актуализацию для slave с версией равной версии в middle
        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyBaseMoney(2, 588486, contractIdSlave,
            versionMiddle, time, Tracking.Portfolio.Action.MONEY_BUY_TRADE, true);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(middleQuantityBaseMoney));
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerYNDX))
            .collect(Collectors.toList());
        assertThat("Action для позиции в портфеле slave не равно", position.get(0).getLastChangeAction().toString(), is("12"));
    }


    //должно быть 5 яндексов по позициям в мидле
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1365098")
    @DisplayName("1365098.HandleActualizeCommand.Version из команды - slave_portfolio.Version текущего портфеля  > 1." +
        "Получаем актуальный портфель из Middle.Version из команды = version из ответа")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля  > 1, Version из команды = version из ответа")
    void C1365098() {
        String SIEBEL_ID_SLAVE = "5-3HYUEXL7";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerYNDX, instrument.classCodeYNDX, instrument.tradingClearingAccountYNDX,
            "3000", "0", "0", "5");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 3, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем  команду на актуализацию для slave
        Tracking.PortfolioCommand commandNew = createCommandActualizeOnlyBaseMoney(2, 500000, contractIdSlave,
            versionMiddle, time, Tracking.Portfolio.Action.ADJUST, true);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, commandNew);
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(middleQuantityBaseMoney));
        List<SlavePortfolio.Position> positionYNDX = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerYNDX))
            .collect(Collectors.toList());
        checkPosition(positionYNDX, instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5");
    }


    //должно быть 5 яндексов по позициям в мидле
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1365612")
    @DisplayName("1365612.HandleActualizeCommand.Version из команды - slave_portfolio.Version текущего портфеля  = 1." +
        "Version из команды = Version из ответа")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.Version текущего портфеля  = 1.Version из команды = Version из ответа")
    void C1365612() {
        String SIEBEL_ID_SLAVE = "5-3HYUEXL7";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerYNDX, instrument.classCodeYNDX, instrument.tradingClearingAccountYNDX,
            "3000", "0", "0", "5");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//       создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        double middleQuantityBaseMoney = getBaseMoneyFromMiddle(clientPositions, "RUB");
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, Tracking.Portfolio.Action.MORNING_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle);
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(middleQuantityBaseMoney));
        List<SlavePortfolio.Position> positionYNDX = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerYNDX))
            .collect(Collectors.toList());
        checkPosition(positionYNDX, instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "5");
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1062109")
    @DisplayName("C1062109.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если позиция и базовая валюта изменились по команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1062109() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveHandleActualizeCommand, instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
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
            if (instrument.tickerAAPL.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
                assertThat("quantity позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getQuantity().toString(), is("5"));
                assertThat("lastChangeAction позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getLastChangeAction(), is((byte) 12));
            }
            //если это позиция, без изменений
            if (instrument.tickerABBV.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
                assertThat("quantity позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getQuantity().toString(), is("1"));
                assertThat("lastChangeAction позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getLastChangeAction(), is(nullValue()));
            }
        }
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    //AAPL=4 USD=11710.6
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1516525")
    @DisplayName("C1516525.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию c застревшей заявкой по прошлой подписке")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1516525() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "117106", "1", "4");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 4, 4,
            baseMoneySlave, date, createListSlavePos);

        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        // создаем застрявшую заявку на прошлу подпску
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, versionMiddle - 4, 1,
            0, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("2"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 117106, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 4, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("11710.6"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(instrument.tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        //проверяем, что отклоняем застрявшую заявку и синхронизируемся на основе нового портфеля:
        // обновляем найденную запись в slave_orderslave_order_2 по полному ключу, проставляя state = 0,
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2CreateAt(contractIdSlave, Date.from(createAtLast.toInstant().truncatedTo(ChronoUnit.SECONDS))), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        //проверяем, что выставилась новая заявка
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder2.getVersion(), is(versionMiddle));
        assertThat("AttemptsCount не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder2.getClassCode(), is(instrument.classCodeAAPL));
        assertThat("IdempotencyKey пустой", slaveOrder2.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(new BigDecimal("4")));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(instrument.tickerAAPL));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder2.getCreateAt().toInstant().truncatedTo(ChronoUnit.MINUTES), is(time.toInstant().truncatedTo(ChronoUnit.MINUTES)));
    }


    //AAPL=4 USD=11710.6
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1517499")
    @DisplayName("C1517499.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на другую стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1517499() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "117106", "1", "4");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        strategyIdNew = UUID.randomUUID();
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerFB, instrument.tradingClearingAccountFB,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyIdNew, versionMiddle - 1, 5,
            baseMoneySlave, date, createListSlavePos);

        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 117106, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 4, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("11710.6"));
//        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
//            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 39));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(instrument.tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder2.getVersion(), is(versionMiddle));
        assertThat("AttemptsCount не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder2.getClassCode(), is(instrument.classCodeAAPL));
        assertThat("IdempotencyKey пустой", slaveOrder2.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(new BigDecimal("4")));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(instrument.tickerAAPL));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder2.getCreateAt().toInstant().truncatedTo(ChronoUnit.MINUTES), is(time.toInstant().truncatedTo(ChronoUnit.MINUTES)));
    }

    //AAPL=4 USD=11710.6
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1517779")
    @DisplayName("C1517779.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию.Только базовая валюта")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1517779() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "117106", "1", "4");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();

        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(1, 117106,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);

        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("11710.6"));
//        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
//            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) 39));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is(nullValue()));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(instrument.tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder2.getVersion(), is(versionMiddle));
        assertThat("AttemptsCount не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder2.getClassCode(), is(instrument.classCodeAAPL));
        assertThat("IdempotencyKey пустой", slaveOrder2.getIdempotencyKey(), is(notNullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(new BigDecimal("4")));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(instrument.tickerAAPL));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder2.getCreateAt(), is(notNullValue()));
    }

    //AAPL=4 USD=11710.6
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1518153")
    @DisplayName("C1518153.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию.Нулевые значения в команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1518153() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "117106", "1", "4");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 0, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 0, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("11710.6"));
//        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
//            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
//        assertThat("changed_at базовой валюты в портфеле slave не равен",
//            slavePortfolio.getBaseMoneyPosition().getChangedAt(), is(nullValue()));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(instrument.tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
    }

    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1518223")
    @DisplayName("C1518223.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если action из команды = 'TRACKING_STATE_UPDATE'.Подписка на ту же стратегию.Только позиция")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1518223() {
        String SIEBEL_ID_SLAVE = "5-ID1PP3JN";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "117106", "1", "4");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 4,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 4, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(4));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("11710.6"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(), is(nullValue()));
        //проверяем позиции
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("4"));
//        assertThat("changed_at позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
//        assertThat("lastChangeAction позиции в портфеле slave не равен", positionAAPL.get(0).getLastChangeAction(), is((byte) 39));
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(instrument.tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("changed_at позиции в портфеле slave не равен", positionABBV.get(0).getChangedAt(), is(nullValue()));
        assertThat("lastChangeAction позиции в портфеле slave не равен", positionABBV.get(0).getLastChangeAction(), is(nullValue()));
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        assertThat("Version портфеля slave не равно", slaveOrder2.getVersion(), is(4));
        assertThat("AttemptsCount не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is("0"));
        assertThat("ClassCode не равно", slaveOrder2.getClassCode(), is(instrument.classCodeAAPL));
        assertThat("IdempotencyKey пустой", slaveOrder2.getIdempotencyKey(), is(notNullValue()));
//        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(new BigDecimal("4")));
//        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
//        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("createAt  не равен", slaveOrder2.getCreateAt(), is(notNullValue()));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1063048")
    @DisplayName("C1063048.HandleActualizeCommand.Формирование актуального набора позиций," +
        " если базовая валюта не изменились по команде")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1063048() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlavePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
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
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    //по договору д.б. 100 USD и 2 AAPL
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1518740")
    @DisplayName("C1518740.HandleActualizeCommand.Version из команды - slave_portfolio.version текущего портфеля  > , action = ADJUST")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1518740() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "100", "0", "2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(),
            is(nullValue()));
        assertThat("ticker позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("TradingClearingAccount позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
    }


    //по договору д.б. 100 USD и 2 AAPL
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1052370")
    @DisplayName("C1052370.HandleActualizeCommand.Первичная инициализация портфеля slave, action != TRACKING_STATE_UPDATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1052370() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "100", "0", "2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        String baseMoneySlave = "6251.10";
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 4,
            baseMoneySlave, date, createListSlavePos);
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        assertThat("lastСhangeAction базовой валюты в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is(nullValue()));
        assertThat("changed_at базовой валюты в портфеле slave не равен",
            slavePortfolio.getBaseMoneyPosition().getChangedAt(),
            is(nullValue()));
        assertThat("ticker позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("TradingClearingAccount позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
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
//        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
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

    //необходимо отрицательное значение по USD, 2-AAPL
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1481900")
    @DisplayName("C1481900.HandleActualizeCommand.Формирование актуального списка позиций из Middle." +
        "Отрицательное значение по базовой валюте")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1481900() {
        //String SIEBEL_ID_SLAVE = "5-CKWQPRIV";
        String SIEBEL_ID_SLAVE = "5-1B1MZMBXO";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "-100", "0", "2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "1", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkSlavePortfolioVersion(versionMiddle);
        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("-100"));
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("2"));
//        assertThat("Quantity позиции в портфеле slave не равна", positionFB.get(0).getQuantity().toString(), is("1"));
    }


    //необходимо отрицательное значение по RUB, 2-AAPL, 100-USD
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1481454")
    @DisplayName("C1481454.HandleActualizeCommand.Формирование актуального списка позиций из Middle." +
        "Отрицательное значение по money.currency = 'RUB'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1481454() {
        String SIEBEL_ID_SLAVE = "1-27UK0AY";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "-1000", "100", "0", "2");
        // String SIEBEL_ID_SLAVE = "5-DXA6EWR9";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "1", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkSlavePortfolioVersion(versionMiddle);
        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("2"));
        assertThat("Количество позиций в портфеле slave не равна", slavePortfolio.getPositions().size(), is(1));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("742580")
    @DisplayName("C742580.HandleActualizeCommand.Актуализация портфеля, без выставления заявки")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742580() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "2", true, true, instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
        checkSlavePortfolioParameters(4, 4, "5855.6");
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем значение changed_at у позиции
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerAAPL.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            }
            if (instrument.tickerABBV.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                assertThat("changed_at позиции в портфеле slave не равен",
                    slavePortfolio.getPositions().get(i).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
                    is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            }
        }
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("731504")
    @DisplayName("C731504.HandleActualizeCommand.Получение подтверждения в полном объеме")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731504() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 3,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "5", price, slavePositionRate, rateDiff,
            quantityDiff, "12");
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("State не равно", slaveOrder2.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder2.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
        assertThat("стратегия не равно", slaveOrder2.getStrategyId(), is(strategyId));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1333799")
    @DisplayName("C1333799.HandleActualizeCommand.Получение подтверждения в полном, если в команде есть позиция с action MONEY_SELL_TRADE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1333799() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
//         создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "3825.9";
        int version = 1;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPos(instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB,
            "8", new BigDecimal("65.4400000000"), new BigDecimal("0.1204"),
            new BigDecimal("-0.1204"), new BigDecimal("-8.0021"), instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "0", new BigDecimal("6133.4"), new BigDecimal("0"),
            new BigDecimal("0.8240"), new BigDecimal("0.5843"), date, 1);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 2,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("8");
        BigDecimal positionQuantity = new BigDecimal("0");
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 1, 1,
            1, instrument.classCodeUSDRUB, 2, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("65.31"), slavePosQuantityBefore,
            null, instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 434827, contractIdSlave,
            version + 1, steps.createPosInCommand(instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(2);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2CreateAt(contractIdSlave, Date.from(createAtLast.toInstant().truncatedTo(ChronoUnit.SECONDS))), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder2.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1366347")
    @DisplayName("C1366347.HandleActualizeCommand.Получение подтверждения в полном, если в команде есть позиция с action MONEY_SELL_TRADE для GBP")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1366347() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
//         создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "3825.9";
        int version = 1;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPos(instrument.tickerGBP, instrument.tradingClearingAccountGBP,
            "8", new BigDecimal("92.5225"), new BigDecimal("0.1204"),
            new BigDecimal("-0.1204"), new BigDecimal("-8.0021"), instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "0", new BigDecimal("6133.4"), new BigDecimal("0"),
            new BigDecimal("0.8240"), new BigDecimal("0.5843"), date, 1);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 2,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("8");
        BigDecimal positionQuantity = new BigDecimal("0");
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 1, 1,
            1, instrument.classCodeGBP, 2, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("65.31"), slavePosQuantityBefore,
            null, instrument.tickerGBP, instrument.tradingClearingAccountGBP);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 434827, contractIdSlave,
            version + 1, steps.createPosInCommand(instrument.tickerGBP, instrument.tradingClearingAccountGBP, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(2);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2CreateAt(contractIdSlave, Date.from(createAtLast.toInstant().truncatedTo(ChronoUnit.SECONDS))), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
        assertThat("version не равно", slaveOrder2.getVersion(), is(version));
        assertThat("attempts_count не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
        assertThat("стратегия не равно", slaveOrder2.getStrategyId(), is(strategyId));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1333801")
    @DisplayName("C1333801.HandleActualizeCommand.Полный объем заявки еще не подтвержден," +
        " в команде есть позиция с action MONEY_SELL_TRADE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1333801() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("275");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB,
            "275", date, 1, new BigDecimal("65.71"), new BigDecimal("0.8253"),
            new BigDecimal("-0.8253"), new BigDecimal("-275.0098"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeUSDRUB, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("65.71"), new BigDecimal("275"),
            null, instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB);
        //формируем команду на актуализацию для slave с количеством по позиции меншь сеи по выставленой заявке
        BigDecimal positionQuantityCommand = new BigDecimal("200");
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB, 200,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantityCommand.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("State не равно", slaveOrder2.getState(), is(nullValue()));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("856826")
    @DisplayName("C856826.HandleActualizeCommand.Полный объем заявки еще не подтвержден")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C856826() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave, но с меньшим количеством по позиции чем выставили в заявке
        BigDecimal positionQuantityCommand = new BigDecimal("2");
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "2", price, slavePositionRate, rateDiff,
            quantityDiff, "12");
        //расчитываем значение filledQuantity
        BigDecimal filledQuantity = (positionQuantityCommand.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("State не равно", slaveOrder2.getState(), is(nullValue()));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1071599")
    @DisplayName("C1071599.HandleActualizeCommand.Получение подтверждения в полном объеме по одной позиции, " +
        "выставление новой заявки по другой позиции")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1071599() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerABBV, instrument.classCodeABBV, instrument.tradingClearingAccountABBV,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 4,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave по позиции с выставленной ранее заявкой
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            3, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        checkSlavePortfolioParameters(3, 3, "6855.6");
//        assertThat("QuantityDiff позиции в портфеле slave не равен", QuantityDiffticker.toString(), is("0"));
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //подтверждаем исполненный объем заявки - обновляем запись, найденную в таблице slave_order_2
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2CreateAt(contractIdSlave, Date.from(createAtLast.toInstant().truncatedTo(ChronoUnit.SECONDS))), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
        //проверяем, что выставилась новая заявка по др позиции
        slaveOrderNew = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("State не равно", slaveOrderNew.getTicker(), is(instrument.tickerAAPL));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1071663")
    @DisplayName("C1071663.HandleActualizeCommand.Получение подтверждения в полном объеме по одной позиции, blocked != false")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1071663() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 4,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //блокируем договор slave
        contract = contractService.updateBlockedContract(contractIdSlave, true);
        steps.createEventInTrackingEventWithBlock(contractIdSlave, true);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            3, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        checkSlavePortfolioParameters(3, 3, "6855.6");
        BigDecimal filledQuantity = (positionQuantity.subtract(slavePosQuantityBefore)).abs();
        BigDecimal updatedFilledQuanitity = new BigDecimal("0").add(filledQuantity);
        //проверяем значения после update в slaveOrder
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2CreateAt(contractIdSlave, Date.from(createAtLast.toInstant().truncatedTo(ChronoUnit.SECONDS))), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("1"));
        assertThat("filledQuantity не равно", slaveOrder2.getFilledQuantity(), is(updatedFilledQuanitity));
        //смотрим, что новая заявка не выставлялась
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(true));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("742614")
    @DisplayName("C742614.HandleActualizeCommand.Синхронизируем портфель, после актуализации.Найдена исполненная заявка state= 1")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742614() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной и исполненной заявке state  1
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("3"),
            (byte) 1, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 6784, contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
        //получаем данные по позиции
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal QuantityDiffticker = position.get(0).getQuantityDiff();
        checkSlavePortfolioParameters(4, 4, "6784");
        // рассчитываем значение;
        BigDecimal lots = QuantityDiffticker.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        checkOrderParameters(4, 4, "0", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.classCodeAAPL);
        assertThat("attempts_count  не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1655049")
    @DisplayName("C1655049.HandleActualizeCommand.Синхронизируем портфель, после актуализации.Найдена отклоненная заявка state = 0 (отклонена)." +
        "Action не совпадает на этапе Выбора позиции для синхронизации")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1655049() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            1, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("1"),
            (byte) 0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerABBV, instrument.tradingClearingAccountABBV, 1,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal QuantityDiffticker = position.get(0).getQuantityDiff();
        checkSlavePortfolioParameters(4, 4, "5855.6");
        // рассчитываем значение;
        BigDecimal lots = QuantityDiffticker.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        //проверяем значения в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        checkOrderParameters(4, 4, "0", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.classCodeAAPL);
        assertThat("attempts_count  не равно", slaveOrder2.getAttemptsCount().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1654601")
    @DisplayName("C1654601.HandleActualizeCommand.Выставление заявки. Найдена неисполненная заявка в slave_order_2," +
        " у которой state = 0. Значение ticker + trading_clearing_account + action = значению ticker + trading_clearing_account + action")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1654601() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной заявке у которой state = 0
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 3, 125,
            0, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("2"),
            (byte) 0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave c позицией и Action совпадающей по выставленной ранее заявке
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 6784, contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
        //получаем данные по позиции
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal QuantityDiffticker = position.get(0).getQuantityDiff();
        //проверяем значения в новой версии портфеля
        checkSlavePortfolioParameters(4, 4, "6784");
        // рассчитываем значение;
        BigDecimal lots = QuantityDiffticker.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        //проверяем значения в slaveOrder, что новая заявка выставилась и attempts_count +1 от ранее выставленной заявке
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("Версия портфеля не равно", slaveOrder2.getVersion(), is(4));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is("0"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(instrument.tickerAAPL));
        assertThat("classCode бумаги не равен", slaveOrder2.getClassCode(), is(instrument.classCodeAAPL));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
        assertThat("attempts_count  не равно", slaveOrder2.getAttemptsCount().toString(), is("126"));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @Repeat(value = 3)
    @AllureId("1366358")
    @DisplayName("C1366358.HandleActualizeCommand.Синхронизируем портфель, после актуализации с валютой")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1366358() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "1", "0", "0", "0");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerHKD, instrument.tradingClearingAccountHKD,
            "27", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            4, steps.createPosInCommand(instrument.tickerCHF, instrument.tradingClearingAccountCHF, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedSlaveVersion(4);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 4), notNullValue());
        BigDecimal QuantityDiffticker1 = BigDecimal.ZERO;
        BigDecimal QuantityDiffticker2 = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerHKD.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker1 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
            if (instrument.tickerYNDX.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                QuantityDiffticker2 = slavePortfolio.getPositions().get(i).getQuantityDiff();
            }
        }
        checkSlavePortfolioParameters(4, 4, "5855.6");
        // рассчитываем значение;
        BigDecimal lots = QuantityDiffticker1.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceBid = new BigDecimal(steps.getPriceFromExchangePositionPriceCache(instrument.tickerHKD, "bid"));
        //проверяем значения в slaveOrder
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        checkOrderParameters(4, 4,"1", lot, lots, priceBid, instrument.tickerHKD, instrument.tradingClearingAccountHKD, instrument.classCodeHKD);
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("742634")
    @DisplayName("C742634.HandleActualizeCommand.Ожидаем подтверждение дальше," +
        " position.action NOT IN ('SECURITY_BUY_TRADE', 'SECURITY_SELL_TRADE')")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742634() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        // создаем запись на выставленную заявку state= null
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 3,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.COUPON_TAX), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "5", price, slavePositionRate, rateDiff,
            quantityDiff, "19");
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("state в slave_order не равно", slaveOrder2.getState(), is(nullValue()));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1249143")
    @DisplayName("C1249143.HandleActualizeCommand.Проверка, можно ли запускать синхронизацию договора, если у подписки blocked = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1249143() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionList);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        List<MasterPortfolio.Position> masterPosTwo = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", instrument.tickerABBV, instrument.tradingClearingAccountABBV, "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6192.9", masterPosTwo);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerABBV, instrument.tradingClearingAccountABBV, 1,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(2);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        checkSlavePortfolioParameters(2, 3, "5855.6");
    }

    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1365590")
    @DisplayName("С1365590.HandleActualizeCommand.Обрабатываем событие с незнакомым enum. Если value незнакомый, то не падаем в ошибку, а должны сохранять int")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE' и не нашли enumAction")
    void C1365590() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //ToDo для корректной работы теста, после изменения схемы, нужно добавить в enum Action значение TEST = 99; в схему tracking.proto
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 588486,
            contractIdSlave, versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.TEST), time, Tracking.Portfolio.Action.TEST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkComparedSlaveVersion(versionMiddle);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        checkSlavePortfolioParameters(versionMiddle, 3, "5884.86");
        assertThat("lastChangeAction BaseMoney не равно", slavePortfolio.getBaseMoneyPosition().getLastChangeAction(), is((byte) Tracking.Portfolio.Action.TEST.getNumber()));
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("5"));
        assertThat("lastChangeAction Position не равно", slavePortfolio.getPositions().get(0).getLastChangeAction(), is((byte) Tracking.Portfolio.Action.TEST.getNumber()));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1365591")
    @DisplayName("С1365591. Получили не известный enum во врема синхронизации)")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1365591() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, 1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, 3,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.TEST), time, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedSlaveVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "5", price, slavePositionRate, rateDiff,
            quantityDiff, String.valueOf(Tracking.Portfolio.Action.TEST.getNumber()));
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("найдена запись в masterPortfolio", slaveOrder2.getState(), is(nullValue()));
    }


    //по договору д.б. 100 USD и 2 AAPL и RUB != 0
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1523191")
    @DisplayName("C1523191.HandleActualizeCommand.Отфильтровываем RUB из ответа метода midle GRPC если получили RUB\n")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1523191() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "1000", "100", "0", "2");
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("100"));
        assertThat("ticker позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("TradingClearingAccount позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("2"));
        //Проверяем, что позициz RUB есть в midle
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            getBaseMoneyFromMiddle(clientPositions, "RUB"), notNullValue());

        //Проверить, что не добавили позицию RUB
        boolean positionotFound = slavePortfolio.getPositions().stream()
            .anyMatch(ps -> ps.getTicker().equals("RUB"));
        assertEquals(positionotFound, false);

        List<SlavePortfolio.Position> positionRUBNotFound = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals("RUB"))
            .collect(Collectors.toList());
        assertEquals(positionRUBNotFound.size(), 0);
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1523957")
    @DisplayName("C1523957.Инициализация портфеля slave данными из ответа метода midle GRPC с базовой валютой RUB")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1523957() {
        //String SIEBEL_ID_SLAVE = "1-FZZU0KU";
        String SIEBEL_ID_SLAVE = "5-88AWFVA2";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "100", "100", "0", "2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX, instrument.tradingClearingAccountYNDX,
            "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //Получаем базовую валюту по стратегии из midle
        int baseMoneySlave = (int) getBaseMoneyFromMiddle(clientPositions, "RUB");
        String baseMoneyPositionSlave = String.valueOf(baseMoneySlave);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 100,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedSlaveVersion(versionMiddle);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        //проверяем параметры портфеля slave
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(versionMiddle));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        //проверяем базовую валюту
        assertThat("базовая валюта в портфеле slave не равно",
            slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneyPositionSlave));
    }


    //USD= 7000
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1616397")
    @DisplayName("C1616397. Обновляем метку старта подписки в событии TRACKING_STATE_UPDATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1616397() {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "7000", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave с временем старта подписки -1c
        OffsetDateTime time = startSubTime.minusSeconds(1);
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, versionMiddle, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(600)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        Subscription getDataFromSubscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //Проверяем обновление startTime подписки (-1c от даты старта подписки и +3ч)
        java.sql.Timestamp getNewStartedAt = new java.sql.Timestamp(time.toInstant().toEpochMilli());
        assertThat("Не обновили время подписки", getDataFromSubscription.getStartTime(), is(getNewStartedAt));
    }


    //по договору д.б. 2156 RUB и 10 SBER
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1596739")
    @DisplayName("С1596739.Определяем актуальный список позиций в портфеле из Middle в событии TRACKING_STATE_UPDATE")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1596739() {
        String SIEBEL_ID_SLAVE = "5-3CGSIDQR";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerSBER, instrument.classCodeSBER, instrument.tradingClearingAccountSBER,
            "2156", "0", "0", "10");
        String baseMoneyPositionSlave = "2156";
        String quantityPos = "10";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusMinutes(7));
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(7);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMinutes(5);
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем версию из middle через запрос по grpc
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave c action = TRACKING_STATE_UPDATE
        //указываем данные по baseMoney количество бумаг и версию не такие как в мидл
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 900,
            contractIdSlave, versionMiddle, steps.createPosInCommand(instrument.tickerSBER, instrument.tradingClearingAccountSBER, 20,
                Tracking.Portfolio.Action.TRACKING_STATE_UPDATE), time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем, что создался портфель для slave с данными актуальными из мидл по запросу grpc
        checkSlavePortfolioParameters(versionMiddle, 3, baseMoneyPositionSlave);
        assertThat("ticker Position не равно", slavePortfolio.getPositions().get(0).getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount Position не равно", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("Quantity Position не равно", slavePortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
    }


    private static Stream<Arguments> secondsForPlus() {
        return Stream.of(
            Arguments.of(0, SubscriptionStatus.active),
            Arguments.of(1, SubscriptionStatus.active),
            Arguments.of(1, SubscriptionStatus.draft),
            Arguments.of(60, SubscriptionStatus.inactive)
        );
    }

    @ParameterizedTest
    @MethodSource("secondsForPlus")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @SneakyThrows
    @AllureId("1616370")
    @DisplayName("C1616370. Не обновляем Метку старта подписки в событии TRACKING_STATE_UPDATE если start_time <= created_at или статус подписки draft")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1616370(int plusSeconds, SubscriptionStatus subscriptionStatus) {
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();

        if (subscriptionStatus.equals(SubscriptionStatus.inactive)) {
            OffsetDateTime endSubTime = OffsetDateTime.now();
            steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
                strategyId, subscriptionStatus, new java.sql.Timestamp(startSubTime.minusSeconds(30).toInstant().toEpochMilli()),
                new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        } else {
            steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
                strategyId, subscriptionStatus, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
                null, false);
        }
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем дату старта подписки
        java.sql.Timestamp subscriptionStartTime = subscription.getStartTime();
        //формируем команду на актуализацию, для slave с временем старта подписки 0c И +1с \ -1c но статус подписки draft \ inactive
        OffsetDateTime createdAt;
        if (subscriptionStatus.equals(SubscriptionStatus.active)) {
            createdAt = startSubTime.plusSeconds(plusSeconds);
        } else {
            createdAt = startSubTime.minusSeconds(plusSeconds);
        }

        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 1, createdAt, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);

        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());

        Subscription getDataFromSubscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //Проверяем, что не обновили метку времени старта подписки
        assertThat("Не обновили время подписки", getDataFromSubscription.getStartTime(), is(subscriptionStartTime));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1616399")
    @DisplayName("C1616399. Не удалось обновить метку start_time в подписке")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1616399() {
        String SIEBEL_ID_SLAVE = "1-FRT3HXX";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "0", "0", "0");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        String periodDefault = "[" + startSubTime.minusDays(2).toLocalDateTime() + "," + startSubTime.minusHours(1).toLocalDateTime() + ")";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //Создаем подписку за прошлый период
        subscription = new Subscription()
            .setSlaveContractId(contractIdSlave)
            .setStrategyId(strategyId)
            .setStartTime(new java.sql.Timestamp(startSubTime.minusDays(2).toInstant().toEpochMilli()))
            .setEndTime(new java.sql.Timestamp(startSubTime.minusHours(2).toInstant().toEpochMilli()))
            .setStatus(SubscriptionStatus.inactive)
            .setBlocked(false);
        subscription = subscriptionService.saveSubscription(subscription);

        //формируем команду на актуализацию для slave с временем старта подписки
        OffsetDateTime time = startSubTime.minusHours(6);
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 1, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);

        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);

        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            contractService.getContract(contractIdSlave).getBlocked(), is(true));
        Contract getContract = contractService.getContract(contractIdSlave);
        //Проверяем блокировку контракта
        assertThat("Не заблокировали контракт", getContract.getBlocked(), is(true));
    }


    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1578407")
    @DisplayName("C1578407.HandleActualizeCommand.Определяем, находится ли портфель slave'а в процессе синхронизации." +
        " Action=TRACKING_STATE_UPDATE. order_state = 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1578407() {
        String SIEBEL_ID_SLAVE = "1-1U3NR90";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "7000", "0", "2");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        strategyIdNew = UUID.randomUUID();
        //делаем запись о выставленной заявке по стратегии, на которую slave был подписан ранее, где state = 0
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyIdNew, 2, 1,
            0, instrument.classCodeFB, 12, new BigDecimal("2"), UUID.randomUUID(),
            UUID.randomUUID(), new BigDecimal("107.79"),  new BigDecimal("2") , (byte) 0,
            instrument.tickerFB, instrument.tradingClearingAccountFB);
        //создаем запись по портфелю на прошлую стратегию
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerFB, instrument.tradingClearingAccountFB,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyIdNew, 2, 12,
            baseMoneySl, date, createListSlaveOnePos);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkSlavePortfolioParameters(versionMiddle, 2, "7000");
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "null");
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем значения в slaveOrder
        checkOrderParameters(versionMiddle, 2,"0", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.classCodeAAPL);
    }


    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1578254")
    @DisplayName("C1578254.HandleActualizeCommand.Определяем, находится ли портфель slave'а в процессе синхронизации." +
        " Отмена застрявшей заявки. action=TRACKING_STATE_UPDATE. order_state = null")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1578254() {
        String SIEBEL_ID_SLAVE = "5-9R5I76TF";
        mocksBasicSteps.createDataForMocksForHandleActualizeCommand(SIEBEL_ID_SLAVE, "2000115978", instrument.tickerAAPL, instrument.classCodeAAPL, instrument.tradingClearingAccountAAPL,
            "0", "7000", "0", "2");
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        strategyIdNew = UUID.randomUUID();
        //делаем запись о выставленной заявке по стратегии, на которую slave был подписан ранее, где state = null
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyIdNew, 2, 1,
            0, instrument.classCodeFB, 12, new BigDecimal("2"), UUID.randomUUID(),
            UUID.randomUUID(), new BigDecimal("107.79"),  new BigDecimal("2"), null,
            instrument.tickerFB, instrument.tradingClearingAccountFB);
        //создаем запись по портфелю на прошлую стратегию
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerFB, instrument.tradingClearingAccountFB,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyIdNew, 2, 12,
            baseMoneySl, date, createListSlaveOnePos);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
        checkSlavePortfolioParameters(versionMiddle, 2, "7000");
        checkPositionParameters(0, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "2", price,
            slavePositionRate, rateDiff, quantityDiff, "null");
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "ask", SIEBEL_ID_SLAVE));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2CreateAt(contractIdSlave, Date.from(createAtLast.toInstant().truncatedTo(ChronoUnit.SECONDS))), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(300)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем значения в slaveOrder
        checkOrderParameters(versionMiddle, 2,"0", lot, lots, priceOrder, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.classCodeAAPL);
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
//        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        slavePortfolio.getPositions().get(pos).getRate().compareTo(slavePositionRate);
//        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
//        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
//        Byte value = lastChangeAction == null ? null : Byte.valueOf(lastChangeAction);
//        assertThat("lastChangeAction позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getLastChangeAction(), is(value));
    }


    public void checkOrderParameters(int version, int masterVersion, String action, BigDecimal lot, BigDecimal lots,
                                     BigDecimal priceOrder, String ticker, String tradingClearingAccount,
                                     String classCode) {
        assertThat("Версия портфеля не равно", slaveOrder2.getVersion(), is(version));
        assertThat("Версия портфеля  мастера не равно", slaveOrder2.getComparedToMasterVersion(), is(masterVersion));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(lots.multiply(lot)));
//        assertThat("price бумаги не равен", slaveOrder2.getPrice(), is(priceOrder));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder2.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
    }


    void checkComparedSlaveVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(2000);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() == version) {
                i = 5;
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
            if ("T365".equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
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


    //проверяем параметры позиции
    public void checkPosition(List<SlavePortfolio.Position> position, String ticker, String tradingClearingAccount, String quantity
    ) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", position.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", position.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", position.get(0).getQuantity().toString(), is(quantity));

    }


    // ожидаем версию портфеля slave
    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3500);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                i = 5;
            }
        }
    }

    // ожидаем версию портфеля slave
    void checkSlavePortfolioVersion(int version) throws InterruptedException {
        Thread.sleep(5000);
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, version);
            if (slavePortfolio.getVersion() != version) {
                ;
            }
        }
    }

    public Instant build(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public Date buildDate(Timestamp timestamp) {
        return Date.from(build(timestamp));
    }
}
