package stpTrackingSlave.handleSynchronizeCommand.SynchronizePositionResolver;


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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
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
    StpTrackingSlaveStepsConfiguration.class
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



    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Client clientSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    long subscriptionId;
    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    //String SIEBEL_ID_SLAVE = "5-JDFC5N71";
    String SIEBEL_ID_SLAVE = "5-CQNPKPNH";


    String tickerShareABBV = "ABBV";
    String tradingClearingAccountShareABBV = "TKCBM_TCAB";
    String classCodeShareABBV = "SPBXM";
    String tickerShareQCOM = "QCOM";
    String tradingClearingAccountShareQCOM = "TKCBM_TCAB";
    String classCodeShareQCOM = "SPBXM";


    String tickerBond = "XS1589324075";
    String tradingClearingAccountBond = "L01+00002F00";
    String classCodeBond = "TQOD";


    String tickerBond1 = "XS0191754729";
    String tradingClearingAccountBond1 = "L01+00002F00";
    String classCodeBond1 = "TQOD";

    String tickerBond2 = "ALFAperp";
    String tradingClearingAccountBond2 = "TKCBM_TCAB";
    String classCodeBond2 = "SPBBND";

    public String tickerGBP = "GBPRUB";
    public String tradingClearingAccountGBP = "MB9885503216";


    String tickerUSD = "USDRUB";
    String tradingClearingAccountUSD = "MB9885503216";
    String classCodeUSD = "EES_CETS";


    String tickerEUR = "EURRUB";
    String tradingClearingAccountEUR = "MB9885503216";
    String classCodeEUR = "EES_CETS";


    String tickerSBER = "SBER";
    String tradingClearingAccountSBER = "L01+00002F00";
    String classCodeSBER = "CETS";

    String tickerGAZP = "GAZP";
    String tradingClearingAccountGAZP = "L01+00002F00";


    String description = "description test стратегия autotest update adjust base currency";

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
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShareABBV, tradingClearingAccountShareABBV,
            "4", tickerShareQCOM, tradingClearingAccountShareQCOM, "10", date, 2,
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
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShareABBV, tradingClearingAccountShareABBV,"3",true, true,
            tickerShareQCOM, tradingClearingAccountShareQCOM, "20", true, true, date);
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
            if (tickerShareQCOM.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerShareQCOM, tradingClearingAccountShareQCOM);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShareQCOM, tradingClearingAccountShareQCOM,
            "10", tickerBond1, tradingClearingAccountBond1, "400", date, 2,
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
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShareQCOM, tradingClearingAccountShareQCOM, "20",true, true,
            tickerBond1, tradingClearingAccountBond1, "600", true, true, date);
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
            if (tickerShareQCOM.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerShareQCOM, tradingClearingAccountShareQCOM);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER,
            "10",  date, 2,steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "12259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave,null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "13657.23";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD, "39",true, true,
            tickerEUR, tradingClearingAccountEUR, "117", true, true, date);
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
            if (tickerUSD.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerUSD, tradingClearingAccountUSD);
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
        steps.createClientWintContractAndStrategy(investIdMaster,null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER, "10", date, 2,
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
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD, "275",true, true,
            tickerSBER, tradingClearingAccountSBER, "50",true, true, date);
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
            if (tickerUSD.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerUSD, tradingClearingAccountUSD);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.MONEY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "12259.17", masterPos);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave,null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "13657.23";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerGBP, tradingClearingAccountGBP, "275",true, true,
            tickerSBER, tradingClearingAccountSBER, "100", true, true, date);
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
            if (tickerGBP.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerGBP, tradingClearingAccountGBP);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShareABBV, tradingClearingAccountShareABBV,
            "4", tickerShareQCOM, tradingClearingAccountShareQCOM, "10", date, 2,
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
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShareABBV,
            tradingClearingAccountShareABBV, "6", true, true, tickerShareQCOM, tradingClearingAccountShareQCOM, "20", true, true, date);
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
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("695957")
    @DisplayName("695957.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache != 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695957() {
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerBond1, tradingClearingAccountBond1,
            "10", tickerBond2, tradingClearingAccountBond2, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerBond1,
            tradingClearingAccountBond1, "20", true, true, tickerBond2, tradingClearingAccountBond2, "10", true, true, date);
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
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerPos, tradingClearingAccountPos);
    }


    @SneakyThrows
    @Test
    @AllureId("695978")
    @DisplayName("C695978.SynchronizePositionResolver.Обрабатываем позиции. Slave_portfolio_position.quantity_diff > 0 и type из exchangePositionCache IN ('bond', 'etf')")
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShareQCOM, tradingClearingAccountShareQCOM,
            "2", tickerBond2, tradingClearingAccountBond2, "6", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "16259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        String baseMoneySlave = "16259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShareQCOM,
            tradingClearingAccountShareQCOM, "1", true, true, tickerBond2, tradingClearingAccountBond2, "4", true, true, date);
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
            if (tickerBond2.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerBond2, tradingClearingAccountBond2);
    }


    @SneakyThrows
    @Test
    @AllureId("695986")
    @DisplayName("695986.SynchronizePositionResolver. Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff > 0 и type из exchangePositionCache IN ('bond', 'etf')")
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerBond1, tradingClearingAccountBond1,
            "20", tickerBond2, tradingClearingAccountBond2, "40", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerBond1,
            tradingClearingAccountBond1, "2", true, true, tickerBond2, tradingClearingAccountBond2, "4", true, true, date);
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
        if (rateList.get(0).compareTo(rateList.get(1)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }
        if (rateList.get(1).compareTo(rateList.get(0)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }
        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShareABBV, tradingClearingAccountShareABBV,
            "20", tickerShareQCOM, tradingClearingAccountShareQCOM, "12", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShareABBV,
            tradingClearingAccountShareABBV, "10", true, true, tickerShareQCOM, tradingClearingAccountShareQCOM, "4", true, true, date);
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
        if (rateList.get(0).compareTo(rateList.get(1)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }
        if (rateList.get(1).compareTo(rateList.get(0)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }
        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
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
        BigDecimal priceAdditional = new BigDecimal("0.002");
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShareABBV, tradingClearingAccountShareABBV,
            "15", tickerShareQCOM, tradingClearingAccountShareQCOM, "35", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "900.5", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "180";
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
        ArrayList<BigDecimal> lotsList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
            lotsList.add(slavePortfolio.getPositions().get(i).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP)
                .multiply(slavePortfolio.getPositions().get(i).getPrice().add(priceAdditional)));
        }
        if (lotsList.get(0).compareTo(new BigDecimal(baseMoneySlave)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }
        if (lotsList.get(1).compareTo(new BigDecimal(baseMoneySlave)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerPos, tradingClearingAccountPos);
    }





    // методы для работы тестов*************************************************************************


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

    void checkParamSlaveOrder(String action, BigDecimal lots, BigDecimal lot, String ticker, String tradingClearingAccount) {
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
    }

    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }
}
