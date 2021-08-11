package stpTrackingSlave.handleSynchronizeCommand.SynchronizePositionResolver;


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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
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



    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "4-1V1UVPX8";
    String SIEBEL_ID_SLAVE = "5-LFCI8UPV";


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
    @AllureId("690419")
    @DisplayName("C690419.SynchronizePositionResolver.Выбор позиции.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C690419() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "TKCBM_TCAB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "TKCBM_TCAB";
        String classCodeShare2 = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        steps.createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        steps.createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShare1, tradingClearingAccountShare1,
            "4", tickerShare2, tradingClearingAccountShare2, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для slave
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShare1, tradingClearingAccountShare1, "3",
            tickerShare2, tradingClearingAccountShare2, "20", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerShare2.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerShare2, tradingClearingAccountShare2);
    }


    @SneakyThrows
    @Test
    @AllureId("695626")
    @DisplayName("C695626.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695626() {
        String tickerBond = "XS1589324075";
        String tradingClearingAccountBond = "L01+00002F00";
        String classCodeBond = "TQOD";
        String tickerShare = "QCOM";
        String tradingClearingAccountShare = "TKCBM_TCAB";
        String classCodeShare = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerBond, classCodeBond, "107.2", "108.2", "105.2");
        steps.createDataToMarketData(tickerShare, classCodeShare, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShare, tradingClearingAccountShare,
            "10", tickerBond, tradingClearingAccountBond, "400", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для slave
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShare, tradingClearingAccountShare, "20",
            tickerBond, tradingClearingAccountBond, "600", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerShare.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, tickerShare, tradingClearingAccountShare);
    }


    @SneakyThrows
    @Test
    @AllureId("695911")
    @DisplayName("695911.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695911() {
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "TKCBM_TCAB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "TKCBM_TCAB";
        String classCodeShare2 = "SPBXM";
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        steps.createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShare1, tradingClearingAccountShare1,
            "4", tickerShare2, tradingClearingAccountShare2, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для slave
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShare1,
            tradingClearingAccountShare1, "6", tickerShare2, tradingClearingAccountShare2, "20", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
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
        String tickerBond1 = "XS0191754729";
        String tradingClearingAccountBond1 = "L01+00002F00";
        String classCodeBond1 = "TQOD";
        String tickerBond2 = "XS1589324075";
        String tradingClearingAccountBond2 = "L01+00002F00";
        String classCodeBond2 = "TQOD";
        BigDecimal lot = new BigDecimal("1");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerBond1, classCodeBond1, "88.3425", "92.9398", "87.3427");
        steps.createDataToMarketData(tickerBond2, classCodeBond2, "104.15", "96", "94.5");
        steps.createEventTrackingTestMdPricesInStream(tickerBond2 + "_" + classCodeBond2, "bid", "101.81", "100.81");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerBond1, tradingClearingAccountBond1,
            "10", tickerBond2, tradingClearingAccountBond2, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerBond1,
            tradingClearingAccountBond1, "20", tickerBond2, tradingClearingAccountBond2, "10", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
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
        String tickerBond = "XS1589324075";
        String tradingClearingAccountBond = "L01+00002F00";
        String classCodeBond = "TQOD";
        String tickerShare = "QCOM";
        String tradingClearingAccountShare = "TKCBM_TCAB";
        String classCodeShare = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerBond, classCodeBond, "107.2", "108.2", "105.2");
        steps.createDataToMarketData(tickerShare, classCodeShare, "55.05", "55.08", "54.82");
        steps.createEventTrackingTestMdPricesInStream(tickerBond + "_" + classCodeBond, "ask", "101.18", "100.18");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShare, tradingClearingAccountShare,
            "2", tickerBond, tradingClearingAccountBond, "6", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "16259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySlave = "16259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShare,
            tradingClearingAccountShare, "1", tickerBond, tradingClearingAccountBond, "4", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerBond.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        //проверяем параметры заявки
        checkParamSlaveOrder("0", lots, lot, tickerBond, tradingClearingAccountBond);
    }


    @SneakyThrows
    @Test
    @AllureId("695986")
    @DisplayName("695986.SynchronizePositionResolver. Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff > 0 и type из exchangePositionCache IN ('bond', 'etf')")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695986() {
        String tickerBond1 = "XS0191754729";
        String tradingClearingAccountBond1 = "L01+00002F00";
        String classCodeBond1 = "TQOD";
        String tickerBond2 = "XS1589324075";
        String tradingClearingAccountBond2 = "L01+00002F00";
        String classCodeBond2 = "TQOD";
        BigDecimal lot = new BigDecimal("1");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerBond1, classCodeBond1, "88.3425", "92.9398", "87.3427");
        steps.createDataToMarketData(tickerBond2, classCodeBond2, "107.2", "108.2", "105.2");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerBond1, tradingClearingAccountBond1,
            "20", tickerBond2, tradingClearingAccountBond2, "40", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerBond1,
            tradingClearingAccountBond1, "2", tickerBond2, tradingClearingAccountBond2, "4", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
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
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "TKCBM_TCAB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "TKCBM_TCAB";
        String classCodeShare2 = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        steps.createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShare1, tradingClearingAccountShare1,
            "20", tickerShare2, tradingClearingAccountShare2, "12", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String baseMoneySlave = "6259.17";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerShare1,
            tradingClearingAccountShare1, "10", tickerShare2, tradingClearingAccountShare2, "4", date);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
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
        //получаем значение lot из ExchangePositionCache
//        BigDecimal lot = new BigDecimal(getLotFromExchangePositionCache(tickerPos,  tradingClearingAccountPos));
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
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "TKCBM_TCAB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "TKCBM_TCAB";
        String classCodeShare2 = "SPBXM";
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal priceAdditional = new BigDecimal("0.002");
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        steps.createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        steps.createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerShare1, tradingClearingAccountShare1,
            "35", tickerShare2, tradingClearingAccountShare2, "35", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "900.5", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "148.3";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
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
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
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
