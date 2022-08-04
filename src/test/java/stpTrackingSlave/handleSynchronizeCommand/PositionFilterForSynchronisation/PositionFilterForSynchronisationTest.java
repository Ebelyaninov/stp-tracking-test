package stpTrackingSlave.handleSynchronizeCommand.PositionFilterForSynchronisation;

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
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMockSlaveDateConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("PositionFilterForSynchronisation - Фильтрация позиций для синхронизации")
@Feature("TAP-6844")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("PositionFilterForSynchronisation")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    MocksBasicStepsConfiguration.class,
    StpTrackingMockSlaveDateConfiguration.class
})
public class PositionFilterForSynchronisationTest {
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
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingSlaveSteps steps;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    SlavePortfolio slavePortfolio;
    SlaveOrder2 slaveOrder2;
    Client clientSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave;
    UUID investIdMaster;
    UUID investIdSlave;
    UUID strategyId;
    long subscriptionId;
    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;
    BigDecimal askPriceAdditionalRate = new BigDecimal("0.002");

    String description = "description: autotest by SynchronizePositionResolverTest";

    public String value;

    @BeforeAll
    void createDataForTests() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdSlaveMaster;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdSlaveSlave;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
    }

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
    @AllureId("1891811")
    @Tags({@Tag("qa")})
    @DisplayName("C1891811.PositionFilterForSynchronisation.Slave_portfolio_position.quantity_diff > 0 buy_enabled = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для фильтрации тех позиций из портфеля, по которым не нужно/нельзя запускать синхронизацию, и формирования конечного списка позиций на синхронизацию.")
    void C1891811() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPosOne);
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL,"2", date, false, false);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        // проверяем, что заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по выставленной заявке не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1891891")
    @Tags({@Tag("qa")})
    @DisplayName("C1891891.PositionFilterForSynchronisation.Slave_portfolio_position.quantity_diff < 0 И sell_enabled = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для фильтрации тех позиций из портфеля, по которым не нужно/нельзя запускать синхронизацию, и формирования конечного списка позиций на синхронизацию.")
    void C1891891() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"5",
            date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPosOne);
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"15", date, false, false);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        // проверяем, что заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по выставленной заявке не равно", order.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("1901231")
    @Tags({@Tag("qa")})
    @DisplayName("C1901231.PositionFilterForSynchronisation.Slave_portfolio_position.quantity_diff = 0 buy_enabled = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для фильтрации тех позиций из портфеля, по которым не нужно/нельзя запускать синхронизацию, и формирования конечного списка позиций на синхронизацию.")
    void C1901231() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPosOne);
        //создаем подписку для  slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "0";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"0", date, true, true);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        // проверяем, что заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по выставленной заявке не равно", order.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("691578")
    @Tags({@Tag("qa")})
    @DisplayName("C691578.PositionFilterForSynchronisation.Позиция не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для фильтрации тех позиций из портфеля, по которым не нужно/нельзя запускать синхронизацию, и формирования конечного списка позиций на синхронизацию.")
    void C691578() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        UUID positionId = UUID.randomUUID();
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerTEST,
            instrument.tradingClearingAccountTEST, positionId,"5", date,
            1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPosOne);
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerTEST,
            instrument.tradingClearingAccountTEST, positionId,"15", date, false, false);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        // проверяем, что заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по выставленной заявке не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("695976")
    @Tags({@Tag("qa")})
    @DisplayName("C695976.PositionFilterForSynchronisation.Slave_portfolio_position.quantity_diff > 0 и tracking_allowed = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для фильтрации тех позиций из портфеля, по которым не нужно/нельзя запускать синхронизацию, и формирования конечного списка позиций на синхронизацию.")
    void C695976() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerTRUR,
            instrument.tradingClearingAccountTRUR, instrument.positionIdTRUR,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPosOne);
        //создаем подписку для slave
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerTRUR,
            instrument.tradingClearingAccountTRUR, instrument.positionIdTRUR,"2", date, true, true);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        // проверяем, что заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по выставленной заявке не равно", order.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("695977")
    @Tags({@Tag("qa")})
    @DisplayName("C695977.PositionFilterForSynchronisation.Slave_portfolio_position.quantity_diff > 0" +
        " И type из exchangePositionCache != 'money' И risk_profile из exchangePositionCache < strategy.risk_profile")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для фильтрации тех позиций из портфеля, по которым не нужно/нельзя запускать синхронизацию, и формирования конечного списка позиций на синхронизацию.")
    void C695977() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6259.17", masterPosOne);
        //создаем подписку для slave
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB, "2", date, true, true);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        // проверяем, что заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по выставленной заявке не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1508875")
    @DisplayName("1508875.PositionFilterForSynchronisation.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и slave_portfolio.positions.quantity < lot")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1508875() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("10");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
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
        String baseMoneySlave = "3000.0";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.positionIdSBER,"8", date, true, true);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        Optional<SlaveOrder2> slaveOrder2 = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        //проверяем, что заявка не выставлялась
        assertThat("выставилась заявка", slaveOrder2.isEmpty());
    }


    @SneakyThrows
    @Test
    @AllureId("1518564")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("1518564.PositionFilterForSynchronisation.Slave_portfolio_position.quantity_diff > 0 " +
        "и slave_portfolio.base_money_position.quantity не достаточно ни для одной позиции")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1518564() {
//        mocksBasicSteps.createDataForMocksForSynchronizePositionResolver(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveSynchronizePositionResolver, instrument.tickerSBER, instrument.classCodeSBER, instrument.tradingClearingAccountSBER,
//            "0", "7000", "0", "0");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту slave в api сервиса счетов
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB,"35", instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"35", date,
            2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
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
        checkComparedToMasterVersion();
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        //рассчитываем число лотов, позиции
        BigDecimal lotsFB = positionFB.get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsAAPL = positionAAPL.get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        assertThat("количество лотов позиции в портфеле slave не равна", lotsFB.toString(), is("0"));
        assertThat("количество лотов позиции в портфеле slave не равна", lotsAAPL.toString(), is("0"));
        //проверяем, что записи по выставленной заявке нет
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("1509911")
    @DisplayName("1509911 Обрабатываем позиции. Slave_portfolio_position.quantity_diff < 0 и slave_portfolio.positions.quantity > lot, min(lots) для продажи")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1509911() {
/*        mocksBasicSteps.createDataForMocksForSynchronizePositionResolver(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveSynchronizePositionResolver, instrument.tickerSBER, instrument.classCodeSBER, instrument.tradingClearingAccountSBER,
            "3000", "0", "0", "0");*/
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("10");
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
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
        String baseMoneySlave = "3000.0";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.positionIdSBER,"20", date, true, true);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
        // рассчитываем значение lots
//        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal minLots = slavePortfolio.getPositions().get(0).getQuantity().divide(lot, 1);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("1", minLots, lot, instrument.tickerSBER, instrument.tradingClearingAccountSBER);
    }


    @SneakyThrows
    @Test
    @AllureId("1510794")
    @DisplayName("1510794 Обрабатываем позиции. Slave_portfolio_position.quantity_diff < 0 и slave_portfolio.positions.quantity = lot")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1510794() {
/*        mocksBasicSteps.createDataForMocksForSynchronizePositionResolver(SIEBEL_ID_SLAVE, stpMockSlaveDate.contractIdSlaveSynchronizePositionResolver, instrument.tickerSBER, instrument.classCodeSBER, instrument.tradingClearingAccountSBER,
            "3000", "0", "0", "0");*/
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("10");
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту slave в api сервиса счетов
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
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
        String baseMoneySlave = "3000.0";
        //создаем список позиций в портфеле slave
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.positionIdSBER,"10", date, true, true);
        //создаем запись в кассандре
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion();
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        checkParamSlaveOrder("1", lots, lot, instrument.tickerSBER, instrument.tradingClearingAccountSBER);
    }



    @SneakyThrows
    @Test
    @AllureId("1901802")
    @Tags({@Tag("qa")})
    @DisplayName("1901802.PositionFilterForSynchronisation. Фильтрация позиций для синхронизации с заблокированной бумаги СПБ")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1901802() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB,"3",
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9000.5", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, "2", true, true,
            instrument.tickerABBV, "L01+00000BLP", null, "1", true, true, date);
        String baseMoneySlave = "16000";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        checkComparedToMasterVersion();
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionALFAperp = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        //рассчитываем число лотов, позиции
        BigDecimal lotsALFAperp = positionALFAperp.get(0).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        //проверяем, торговый статус позиции
        String lastStatus = steps.getStatusFromExchangePositionTradingStatusCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp);
        if (instrument.tradingStatusesFalse.contains(lastStatus)) {
            //проверяем, что записи по выставленной заявке нет
            Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
            assertThat("запись по портфелю не равно", order.isPresent(), is(false));
        }
        if (instrument.tradingStatusesTrue.contains(lastStatus)) {
            //проверяем значения в slaveOrder2
            await().atMost(TEN_SECONDS).until(() ->
                slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
            checkOrderParameters(1, 2, "1", lotsALFAperp, instrument.tickerALFAperp,
                instrument.tradingClearingAccountALFAperp, instrument.classCodeALFAperp);
        }
    }


    //для теста необходимо, чтобы одна из бирж не работала
    @SneakyThrows
    @Test
    @AllureId("1901763")
    @Tags({@Tag("qa")})
    @DisplayName("1901763.PositionFilterForSynchronisation.Оставляем только те позиции, " +
        "для которых сейчас работает торговая площадка exchange")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1901763() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB,"3", instrument.tickerXS0191754729,
            instrument.tradingClearingAccountXS0191754729,  instrument.positionIdXS0191754729,"5", date,
            2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9000.5", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "16000";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        checkComparedToMasterVersion();
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerFB))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionXS0191754729 = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0191754729))
            .collect(Collectors.toList());
        //проверяем значения в slaveOrder2
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }





    @SneakyThrows
    @Test
    @AllureId("1891547")
    @Tags({@Tag("qa")})
    @DisplayName("1891547.PositionFilterForSynchronisation.Не найден статус торгов по позиции в кеш exchangePositionTradingStatusCache")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C1891547() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"3", instrument.tickerNOK,
            instrument.tradingClearingAccountNOK,  instrument.positionIdNOK,"5", date,
            2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9000.5", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> createListSlavePos = new ArrayList<>();
        String baseMoneySlave = "16000";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlavePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        checkComparedToMasterVersion();
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionNOK = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerNOK))
            .collect(Collectors.toList());
        BigDecimal quantityDiff = positionAAPL.get(0).getQuantityDiff();
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        //проверяем параметры заявки
        assertThat("позиция с ненайденным торговым статусом не равна", positionNOK.size(), is(1));
        //проверяем значения в slaveOrder2
        await().atMost(TEN_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        checkParamSlaveOrder("0", lots, lot, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
    }



    @Step("Проверяем выставленной заявки: ")
    void checkParamSlaveOrder(String action, BigDecimal lots, BigDecimal lot, String ticker, String tradingClearingAccount) {
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(ticker));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(tradingClearingAccount));
    }


    @Step("Ожидаем записи в slave_portfolio: ")
    void checkComparedToMasterVersion() throws InterruptedException {
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
    }

    @Step("Проверяем параметры выставленной заявки: ")
    public void checkOrderParameters(int version, int masterVersion, String action, BigDecimal lots,
                                      String ticker, String tradingClearingAccount, String classCode) {
        assertThat("Версия портфеля не равно", slaveOrder2.getVersion(), is(version));
        assertThat("Версия портфеля  мастера не равно", slaveOrder2.getComparedToMasterVersion(), is(masterVersion));
        assertThat("Направление заявки Action не равно", slaveOrder2.getAction().toString(), is(action));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder2.getQuantity(), is(lots));
        assertThat("ticker бумаги не равен", slaveOrder2.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder2.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder2.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("filled_quantity  не равен", slaveOrder2.getFilledQuantity(), is(new BigDecimal("0")));
    }

}
