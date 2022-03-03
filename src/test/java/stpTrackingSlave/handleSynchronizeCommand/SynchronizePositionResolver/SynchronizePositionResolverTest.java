package stpTrackingSlave.handleSynchronizeCommand.SynchronizePositionResolver;


import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Comparators.min;
import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("handleSynchronizeCommand - Выбор позиции для синхронизации")
@Feature("TAP-6844")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleSynchronizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
})

public class SynchronizePositionResolverTest {
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
    StpInstrument instrument;
    @Autowired
    MocksBasicSteps mocksBasicSteps;

    SlavePortfolio slavePortfolio;
    SlaveOrder2 slaveOrder2;
    Client clientSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    long subscriptionId;
    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    String SIEBEL_ID_SLAVE = "5-CQNPKPNH";
    BigDecimal askPriceAdditionalRate = new BigDecimal("0.002");

    String description = "description: autotest by SynchronizePositionResolverTest";

    public String value;

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
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("690419")
    @DisplayName("C690419.SynchronizePositionResolver.Выбор позиции.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C690419() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "4", instrument.tickerQCOM, instrument.tradingClearingAccountQCOM, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3", true, true,
            instrument.tickerQCOM, instrument.tradingClearingAccountQCOM, "20", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerQCOM.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, instrument.tickerQCOM, instrument.tradingClearingAccountQCOM);
    }


    @SneakyThrows
    @Test
    @AllureId("695626")
    @DisplayName("C695626.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695626() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerQCOM,
            instrument.tradingClearingAccountQCOM, "10", instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "400", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerQCOM,
            instrument.tradingClearingAccountQCOM, "20", true, true,
            instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "600", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerQCOM.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, instrument.tickerQCOM, instrument.tradingClearingAccountQCOM);
    }


    @SneakyThrows
    @Test
    @AllureId("1323820")
    @DisplayName("C1323820.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'money'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1323820() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "12259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "13657.23";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "39", true, true,
            instrument.tickerEURRUB, instrument.tradingClearingAccountEURRUB, "117", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerUSDRUB.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB);
    }


    @SneakyThrows
    @Test
    @AllureId("1323880")
    @DisplayName("C1323880.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'money' и 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1323880() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.MONEY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "12259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "13657.23";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "275", true, true,
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "50", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerUSDRUB.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB);
    }


    @SneakyThrows
    @Test
    @AllureId("1349227")
    @DisplayName("1349227.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'money' GBP и 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1349227() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.MONEY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "12259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "13657.23";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerGBP,
            instrument.tradingClearingAccountGBP, "275", true, true,
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, "100", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerGBP.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, instrument.tickerGBP, instrument.tradingClearingAccountGBP);
    }


    @SneakyThrows
    @Test
    @AllureId("695911")
    @DisplayName("695911.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695911() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "4", instrument.tickerQCOM, instrument.tradingClearingAccountQCOM,
            "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "6", true, true,
            instrument.tickerQCOM, instrument.tradingClearingAccountQCOM, "20", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        ArrayList<BigDecimal> rateList = new ArrayList<>();
        ArrayList<BigDecimal> priceList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
        }

        if (rateList.get(0).compareTo(rateList.get(1)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }

        if (rateList.get(1).compareTo(rateList.get(0)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }

        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) < 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) < 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("695957")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("695957.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache != 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695957() {
        mocksBasicSteps.createDataForMocksTestC695957(SIEBEL_ID_SLAVE, instrument.tickerAAPL, instrument.classCodeAAPL, "0", "2");
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729,
            "10", instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "20", true, true,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "10", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionXS0191754729 = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionALFAperp = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        //выбираем позицию для выставления заявки
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        BigDecimal quantity = BigDecimal.ZERO;
        if (positionXS0191754729.get(0).getRate().compareTo(positionALFAperp.get(0).getRate()) < 0) {
            quantityDiff = positionXS0191754729.get(0).getQuantityDiff();
            tickerPos = positionXS0191754729.get(0).getTicker();
            tradingClearingAccountPos = positionXS0191754729.get(0).getTradingClearingAccount();
            quantity = positionXS0191754729.get(0).getQuantity();
        } else {
            quantityDiff = positionALFAperp.get(0).getQuantityDiff();
            tickerPos = positionALFAperp.get(0).getTicker();
            tradingClearingAccountPos = positionALFAperp.get(0).getTradingClearingAccount();
            quantity = positionALFAperp.get(0).getQuantity();
        }
        if (positionXS0191754729.get(0).getRate().compareTo(positionALFAperp.get(0).getRate()) == 0) {
            if (positionXS0191754729.get(0).getPrice().compareTo(positionALFAperp.get(0).getPrice()) < 0) {
                quantityDiff = positionXS0191754729.get(0).getQuantityDiff();
                tickerPos = positionXS0191754729.get(0).getTicker();
                tradingClearingAccountPos = positionXS0191754729.get(0).getTradingClearingAccount();
                quantity = positionXS0191754729.get(0).getQuantity();
            } else {
                quantityDiff = positionALFAperp.get(0).getQuantityDiff();
                tickerPos = positionALFAperp.get(0).getTicker();
                tradingClearingAccountPos = positionALFAperp.get(0).getTradingClearingAccount();
                quantity = positionALFAperp.get(0).getQuantity();
            }
        }
        // рассчитываем значение lots
        BigDecimal lotsСalc = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lots = quantity.divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = min(lots, lotsСalc);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lotsMax, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("695978")
    @DisplayName("C695978.SynchronizePositionResolver.Обрабатываем позиции. Slave_portfolio_position.quantity_diff > 0 " +
        "и type из exchangePositionCache IN ('bond', 'etf')")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695978() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerQCOM,
            instrument.tradingClearingAccountQCOM, "2", instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp,
            "6", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "16259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        String baseMoneySlave = "16259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerQCOM,
            instrument.tradingClearingAccountQCOM, "1", true, true,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "4", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (instrument.tickerALFAperp.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        List<SlavePortfolio.Position> positionALFAperp = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        // рассчитываем значение lots
        BigDecimal lotsСalc = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lots = positionALFAperp.get(0).getQuantity().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = min(lots, lotsСalc);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lotsMax, lot, instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp);
    }


    @SneakyThrows
    @Test
    @AllureId("695986")
    @DisplayName("695986.SynchronizePositionResolver. Обрабатываем позиции.Несколько позиций, " +
        "у которых slave_portfolio_position.quantity_diff > 0 и type из exchangePositionCache IN ('bond', 'etf')")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695986() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "20", instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, "40", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729, "2", true, true,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "4", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionXS0191754729 = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionALFAperp = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());

        //считаем сколько денег в резерве
        BigDecimal moneyReservePortfolio = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .subtract(slavePortfolio.getActualFeeReserveQuantity());
        //проверяем, хватает ли денег на покупку позиций AAPL:
        BigDecimal moneyToBuyXS0191754729 = lot.multiply(positionXS0191754729.get(0).getPrice()
            .add(positionXS0191754729.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //проверяем, хватает ли денег на покупку позиций ABBV:
        BigDecimal moneyToBuyALFAperp = lot.multiply(positionALFAperp.get(0).getPrice()
            .add(positionALFAperp.get(0).getPrice().multiply(askPriceAdditionalRate)));

        //выбираем позицию для выставления заявки
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        BigDecimal quantity = BigDecimal.ZERO;
        if (positionXS0191754729.get(0).getRate().compareTo(positionALFAperp.get(0).getRate()) > 0) {
            quantityDiff = positionXS0191754729.get(0).getQuantityDiff();
            tickerPos = positionXS0191754729.get(0).getTicker();
            tradingClearingAccountPos = positionXS0191754729.get(0).getTradingClearingAccount();
        } else {
            quantityDiff = positionALFAperp.get(0).getQuantityDiff();
            tickerPos = positionALFAperp.get(0).getTicker();
            tradingClearingAccountPos = positionALFAperp.get(0).getTradingClearingAccount();
        }
        if (positionXS0191754729.get(0).getRate().compareTo(positionALFAperp.get(0).getRate()) == 0) {
            if (positionXS0191754729.get(0).getPrice().compareTo(positionALFAperp.get(0).getPrice()) > 0) {
                quantityDiff = positionXS0191754729.get(0).getQuantityDiff();
                tickerPos = positionXS0191754729.get(0).getTicker();
                tradingClearingAccountPos = positionXS0191754729.get(0).getTradingClearingAccount();
            } else {
                quantityDiff = positionALFAperp.get(0).getQuantityDiff();
                tickerPos = positionALFAperp.get(0).getTicker();
                tradingClearingAccountPos = positionALFAperp.get(0).getTradingClearingAccount();
            }
        }

        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = BigDecimal.ZERO;
        //рассчитываем максимальное число лотов, доступных для покупки
        if (tickerPos.equals(instrument.tickerXS0191754729)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyXS0191754729, 0, RoundingMode.DOWN);
        }
        if (tickerPos.equals(instrument.tickerALFAperp)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyALFAperp, 0, RoundingMode.DOWN);
        }
        lots = min(lots, lotsMax);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("697301")
    @DisplayName("6697301.SynchronizePositionResolver.Обрабатываем позиции. Slave_portfolio_position.quantity_diff > 0 и type = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C697301() {
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "20", instrument.tickerQCOM,
            instrument.tradingClearingAccountQCOM, "12", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "10", true, true,
            instrument.tickerQCOM, instrument.tradingClearingAccountQCOM, "4", true, true, date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionQCOM = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerQCOM))
            .collect(Collectors.toList());
        //считаем сколько денег в резерве
        BigDecimal moneyReservePortfolio = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .subtract(slavePortfolio.getActualFeeReserveQuantity());
        //проверяем, хватает ли денег на покупку позиций ABBV:
        BigDecimal moneyToBuyABBV = lot.multiply(positionABBV.get(0).getPrice()
            .add(positionABBV.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //проверяем, хватает ли денег на покупку позиций QCOM:
        BigDecimal moneyToBuyQCOM = lot.multiply(positionQCOM.get(0).getPrice()
            .add(positionQCOM.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //выбираем позицию для выставления заявки
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        BigDecimal quantity = BigDecimal.ZERO;
        if (positionABBV.get(0).getRate().compareTo(positionQCOM.get(0).getRate()) > 0) {
            quantityDiff = positionABBV.get(0).getQuantityDiff();
            tickerPos = positionABBV.get(0).getTicker();
            tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
        } else {
            quantityDiff = positionQCOM.get(0).getQuantityDiff();
            tickerPos = positionQCOM.get(0).getTicker();
            tradingClearingAccountPos = positionQCOM.get(0).getTradingClearingAccount();
        }
        if (positionABBV.get(0).getRate().compareTo(positionQCOM.get(0).getRate()) == 0) {
            if (positionABBV.get(0).getPrice().compareTo(positionQCOM.get(0).getPrice()) > 0) {
                quantityDiff = positionABBV.get(0).getQuantityDiff();
                tickerPos = positionABBV.get(0).getTicker();
                tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
            } else {
                quantityDiff = positionQCOM.get(0).getQuantityDiff();
                tickerPos = positionQCOM.get(0).getTicker();
                tradingClearingAccountPos = positionQCOM.get(0).getTradingClearingAccount();
            }
        }

        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = BigDecimal.ZERO;
        //рассчитываем максимальное число лотов, доступных для покупки
        if (tickerPos.equals(instrument.tickerABBV)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyABBV, 0, RoundingMode.DOWN);
        }
        if (tickerPos.equals(instrument.tickerQCOM)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyQCOM, 0, RoundingMode.DOWN);
        }
        lots = min(lots, lotsMax);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("697225")
    @DisplayName("697225.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций," +
        " у которых slave_portfolio_position.quantity_diff > 0,первая позиция списка, для покупки которой хватает денег")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C697225() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "35", instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "35", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "900.5", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "250";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        //считаем сколько денег в резерве
        BigDecimal moneyReservePortfolio = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .subtract(slavePortfolio.getActualFeeReserveQuantity());
        //проверяем, хватает ли денег на покупку позиций ABBV:
        BigDecimal moneyToBuyABBV = lot.multiply(positionABBV.get(0).getPrice()
            .add(positionABBV.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //проверяем, хватает ли денег на покупку позиций AAPL:
        BigDecimal moneyToBuyAAPL = lot.multiply(positionAAPL.get(0).getPrice()
            .add(positionAAPL.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //рассчитываем число лотов, позиции
        BigDecimal lotsABBV = positionABBV.get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        //выбираем позицию для выставления заявки
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        if ((lotsABBV.multiply(positionABBV.get(0).getPrice().add(askPriceAdditionalRate))).compareTo(moneyReservePortfolio) < 0) {
            quantityDiff = positionABBV.get(0).getQuantityDiff();
            tickerPos = positionABBV.get(0).getTicker();
            tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
        } else {
            quantityDiff = positionAAPL.get(0).getQuantityDiff();
            tickerPos = positionAAPL.get(0).getTicker();
            tradingClearingAccountPos = positionAAPL.get(0).getTradingClearingAccount();
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = BigDecimal.ZERO;
        //рассчитываем максимальное число лотов, доступных для покупки
        if (tickerPos.equals(instrument.tickerABBV)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyABBV, 0, RoundingMode.DOWN);
        }
        if (tickerPos.equals(instrument.tickerAAPL)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyAAPL, 0, RoundingMode.DOWN);
        }
        lots = min(lots, lotsMax);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("1518574")
    @DisplayName("1518574.SynchronizePositionResolver.Slave_portfolio_position.quantity_diff > 0" +
        " и slave_portfolio.base_money_position.quantity достаточно для 1 lot первой позиции и 1 lot второй позиции")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1518574() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "15", instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "35", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "5759.2";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());

        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        //проверяем, хватает ли денег на покупку позиций AAPL:
        BigDecimal moneyToBuyAAPL = lot.multiply(positionAAPL.get(0).getPrice()
            .add(positionAAPL.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //проверяем, хватает ли денег на покупку позиций ABBV:
        BigDecimal moneyToBuyABBV = lot.multiply(positionABBV.get(0).getPrice()
            .add(positionABBV.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //считаем сколько денег в резерве
        BigDecimal moneyReservePortfolio = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .subtract(slavePortfolio.getActualFeeReserveQuantity());
        if (moneyReservePortfolio.compareTo(moneyToBuyAAPL) >= 0) {
            log.info("в портфеле есть достаточное количество базовой валюты на покупку хотя бы одного лота  {}", moneyToBuyAAPL);
        }
        if (moneyReservePortfolio.compareTo(moneyToBuyABBV) >= 0) {
            log.info("в портфеле есть достаточное количество базовой валюты на покупку хотя бы одного лота  {}", moneyToBuyABBV);
        }
        //получаем позицию для покупки с приоритизацией по rate  или price
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        BigDecimal quantityDiff = BigDecimal.ZERO;
        if (positionAAPL.get(0).getRateDiff().compareTo(positionABBV.get(0).getRateDiff()) > 0) {
            quantityDiff = positionAAPL.get(0).getQuantityDiff();
            tickerPos = positionAAPL.get(0).getTicker();
            tradingClearingAccountPos = positionAAPL.get(0).getTradingClearingAccount();
        } else {
            quantityDiff = positionABBV.get(0).getQuantityDiff();
            tickerPos = positionABBV.get(0).getTicker();
            tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
        }

        if (positionAAPL.get(0).getRateDiff().compareTo(positionABBV.get(0).getRateDiff()) == 0) {
            if (positionAAPL.get(0).getPrice().compareTo(positionABBV.get(0).getPrice()) > 0) {
                quantityDiff = positionAAPL.get(0).getQuantityDiff();
                tickerPos = positionAAPL.get(0).getTicker();
                tradingClearingAccountPos = positionAAPL.get(0).getTradingClearingAccount();
            } else {
                quantityDiff = positionABBV.get(0).getQuantityDiff();
                tickerPos = positionABBV.get(0).getTicker();
                tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = BigDecimal.ZERO;
        //рассчитываем максимальное число лотов, доступных для покупки
        if (tickerPos.equals(instrument.tickerAAPL)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyAAPL, 0, RoundingMode.DOWN);
        }
        if (tickerPos.equals(instrument.tickerABBV)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyABBV, 0, RoundingMode.DOWN);
        }
        lots = min(lots, lotsMax);
        //проверяем, что выставили заявку по выбранной позиции и правильным числом лотов
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("1695490")
    @DisplayName("1695490.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff > 0" +
        " выбираем максимальное число лотов, на которое хватает свободных денег")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1695490() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "4500", instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "700", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "351.1", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "1759.2";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        //считаем сколько денег в резерве
        BigDecimal moneyReservePortfolio = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .subtract(slavePortfolio.getActualFeeReserveQuantity());
        //проверяем, хватает ли денег на покупку позиций AAPL:
        BigDecimal moneyToBuyAAPL = lot.multiply(positionAAPL.get(0).getPrice()
            .add(positionAAPL.get(0).getPrice().multiply(askPriceAdditionalRate)));
        //проверяем, хватает ли денег на покупку позиций ABBV:
        BigDecimal moneyToBuyABBV = lot.multiply(positionABBV.get(0).getPrice()
            .add(positionABBV.get(0).getPrice().multiply(askPriceAdditionalRate)));
        if (moneyReservePortfolio.compareTo(moneyToBuyAAPL) >= 0) {
            log.info("в портфеле есть достаточное количество базовой валюты на покупку хотя бы одного лота  {}", moneyToBuyAAPL);
        }
        if (moneyReservePortfolio.compareTo(moneyToBuyABBV) >= 0) {
            log.info("в портфеле есть достаточное количество базовой валюты на покупку хотя бы одного лота  {}", moneyToBuyABBV);
        }
        //получаем позицию для покупки с приоритизацией по rate  или price
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        BigDecimal quantityDiff = BigDecimal.ZERO;
        if (positionAAPL.get(0).getRateDiff().compareTo(positionABBV.get(0).getRateDiff()) > 0) {
            quantityDiff = positionAAPL.get(0).getQuantityDiff();
            tickerPos = positionAAPL.get(0).getTicker();
            tradingClearingAccountPos = positionAAPL.get(0).getTradingClearingAccount();
        } else {
            quantityDiff = positionABBV.get(0).getQuantityDiff();
            tickerPos = positionABBV.get(0).getTicker();
            tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
        }
        if (positionAAPL.get(0).getRateDiff().compareTo(positionABBV.get(0).getRateDiff()) == 0) {
            if (positionAAPL.get(0).getPrice().compareTo(positionABBV.get(0).getPrice()) > 0) {
                quantityDiff = positionAAPL.get(0).getQuantityDiff();
                tickerPos = positionAAPL.get(0).getTicker();
                tradingClearingAccountPos = positionAAPL.get(0).getTradingClearingAccount();
            } else {
                quantityDiff = positionABBV.get(0).getQuantityDiff();
                tickerPos = positionABBV.get(0).getTicker();
                tradingClearingAccountPos = positionABBV.get(0).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = BigDecimal.ZERO;
        //рассчитываем максимальное число лотов, доступных для покупки
        if (tickerPos.equals(instrument.tickerAAPL)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyAAPL, 0, RoundingMode.DOWN);
        }
        if (tickerPos.equals(instrument.tickerABBV)) {
            lotsMax = moneyReservePortfolio.divide(moneyToBuyABBV, 0, RoundingMode.DOWN);
        }
        lots = min(lots, lotsMax);
        //проверяем, что выставили заявку по выбранной позиции и правильным числом лотов
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("1518564")
    @DisplayName("1518564.SynchronizePositionResolver. Slave_portfolio_position.quantity_diff > 0 " +
        "и slave_portfolio.base_money_position.quantity не достаточно ни для одной позиции")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1518564() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "35", instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "35", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "900.5", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "100";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        //рассчитываем число лотов, позиции
        BigDecimal lotsABBV = positionABBV.get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsAAPL = positionAAPL.get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        assertThat("количество лотов позиции в портфеле slave не равна", lotsABBV.toString(), is("0"));
        assertThat("количество лотов позиции в портфеле slave не равна", lotsAAPL.toString(), is("0"));
        //проверяем, что записи по выставленной заявке нет
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }


    // методы для работы тестов*************************************************************************

    @Step("Проверяем параметры позиции: ")
    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        Integer synchronizedToMasterVersion, BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal masterPositionRate, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("SynchronizedToMasterVersion позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getSynchronizedToMasterVersion(), is(synchronizedToMasterVersion));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(masterPositionRate));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));

    }

    @Step("Проверяем выставленной заявки: ")
    void checkParamSlaveOrder(String action, BigDecimal lots, BigDecimal lot, String ticker, String tradingClearingAccount) {
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(ticker));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(tradingClearingAccount));
    }


    @Step("Ожидаем записи в slave_portfolio: ")
    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3000);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }
}
