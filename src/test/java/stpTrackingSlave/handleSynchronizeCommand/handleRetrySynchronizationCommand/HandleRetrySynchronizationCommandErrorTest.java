package stpTrackingSlave.handleSynchronizeCommand.handleRetrySynchronizationCommand;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
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
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

@Slf4j
@Epic("handleSynchronizeCommand - Обработка команд на повторную синхронизацию")
@Feature("TAP-6843")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class HandleRetrySynchronizationCommandErrorTest {
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService kafkaStringSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
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
    Contract contract;
    String contractIdMaster;
    String contractIdSlave = "2039667312";
    UUID strategyId;
    String SIEBEL_ID_MASTER = "4-1V1UVPX8";
    String SIEBEL_ID_SLAVE = "5-1HE55RPOV";
    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";


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
    @AllureId("739012")
    @DisplayName("C739012.HandleRetrySynchronizationCommand.Ошибка на анализе портфеля slave'а относительно портфеля master'а")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
    void C739012() {
        String ticker = "TECH";
        String tradingClearingAccount = "L01+00002F00";
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//       создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
            new BigDecimal("-0.0211"), new BigDecimal("-2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_EVENT);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //смотрим, что заявка не выставлялась
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
        // ловим событие о блокировке slave в топике tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkParamEvent(event, "UPDATED", "TRACKED", true);
        //находим в БД автоследования contract
        contract = contractService.getContract(contractIdSlave);
        checkParamContract("tracked", true);
    }


    @SneakyThrows
    @Test
    @AllureId("739015")
    @DisplayName("C739015.HandleRetrySynchronizationCommand.Ошибка при выставлении заявки")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
    void C739015() {
        String ticker = "WLH";
        String tradingClearingAccount = "TKCBM_TCAB";
        String classCode = "SPBXM";
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        steps.createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "last", "108.09", "107.79");
        steps.createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "ask", "107.09", "108.56");
        steps.createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "bid", "108.11", "108.32");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку для slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "7", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0964"),
            new BigDecimal("-0.0211"), new BigDecimal("-2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_EVENT);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //смотрим заявку
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        // ловим событие о блокировке slave в топике tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkParamEvent(event, "UPDATED", "TRACKED", true);
        //находим в БД автоследования contract
        contract = contractService.getContract(contractIdSlave);
        checkParamContract("tracked", true);
    }


    @SneakyThrows
    @Test
    @AllureId("739006")
    @DisplayName("C739006.HandleRetrySynchronizationCommand.У contractId blocked = true")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
    void C739006() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        steps.createDataToMarketData(ticker, classCode, "108.09", "107.79", "107.72");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//       создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "7", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для slave
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "3", date, 1, new BigDecimal("108.11"), new BigDecimal("0.0443"),
            new BigDecimal("0.593"), new BigDecimal("4"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        steps.createEventInTrackingEventWithBlock(contractIdSlave, true);
        contract = contractService.updateBlockedContract(contractIdSlave, true);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //смотрим, что заявка не выставлялась
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
        assertThat("запись по заявке не равно", order.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("739008")
    @DisplayName("C739008.HandleRetrySynchronizationCommand.Не найден портфель slave'a в бд Cassandra в таблице slave_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на повторную синхронизацию slave-портфеля.")
    void C739008() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        steps.createDataToMarketData(ticker, classCode, "108.09", "107.79", "107.72");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createEventInTrackingEventWithBlock(contractIdSlave, false);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "7", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.1", masterPos);
        //создаем подписку slave
        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_EVENT);
        //отправляем команду на  повторную синхронизацию
        steps.createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //смотрим, что заявка не выставлялась
        Optional<SlaveOrder> order = slaveOrderDao.findSlaveOrder(contractIdMaster, strategyId);
        assertThat("запись по заявке не равно", order.isPresent(), is(false));
        // ловим событие о блокировке slave в топике tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkParamEvent(event, "UPDATED", "TRACKED", true);
        //находим в БД автоследования contract
        contract = contractService.getContract(contractIdSlave);
        checkParamContract("tracked", true);
    }


//методы для тестов*************************************************************************************

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

    public void checkSlavePortfolioParameters(int version, int comparedToMasterVersion, String baseMoney) {
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(version));
        assertThat("ComparedToMasterVersion в портфеле slave не равно", slavePortfolio.getComparedToMasterVersion(), is(comparedToMasterVersion));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney));
    }

    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal rateDiff, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }


    void checkParamEvent(Tracking.Event event, String action, String state, boolean blocked) {
        assertThat("Action события не равен", event.getAction().toString(), is(action));
        assertThat("State договора не равен", event.getContract().getState().toString(), is(state));
        assertThat("Blocked договора не равен", (event.getContract().getBlocked()), is(blocked));
    }

    void checkParamContract(String state, boolean blocked) {
        assertThat("статус клиента не равно", (contract.getState()).toString(), is(state));
        assertThat("Blocked клиента не равно", (contract.getBlocked()), is(blocked));
    }


}
