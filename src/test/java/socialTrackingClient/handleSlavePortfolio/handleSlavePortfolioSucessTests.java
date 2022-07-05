package socialTrackingClient.handleSlavePortfolio;


import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.*;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.tracking.slave.portfolio.SlavePortfolioOuterClass;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;


@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleSlavePortfolio - Обработка изменений портфелей ведомых")
@Feature("TAP-11008")
@DisplayName("social-tracking-client")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Owner("ext.ebelyaninov")
@Tags({@Tag("social-tracking-client"),@Tag("handleSlavePortfolio")})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingMasterStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class
})
public class handleSlavePortfolioSucessTests {

    UtilsTest utilsTest = new UtilsTest();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps stpTrackingApiSteps;
    @Autowired
    ContractService contractService;
    @Autowired
    StringToByteSenderService stringToByteSenderService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingSlaveSteps stpTrackingSlaveSteps;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpInstrument instrument;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;


    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;
    String contractIdMaster;
    String contractIdSlave;
    UUID investIdMaster;
    UUID investIdSlave;
    UUID strategyId = UUID.randomUUID();
    String title = "Cтратегия для " + SIEBEL_ID_MASTER;
    String description = "new test стратегия autotest";
    Long subscriptionId;
    LocalDateTime currentDate = (LocalDateTime.now());
    String periodDefoult = "[" + currentDate + ",)";

    Subscription subscription;
    MasterPortfolioValue masterPortfolioValue;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdMasterForClient;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdSlaveForClient;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = stpTrackingApiSteps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountAgressive = stpTrackingApiSteps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        investIdSlave = resAccountAgressive.getInvestId();
        contractIdSlave = resAccountAgressive.getBrokerAccounts().get(0).getId();
        stpTrackingApiSteps.deleteDataFromDb(SIEBEL_ID_MASTER);
        stpTrackingApiSteps.deleteDataFromDb(SIEBEL_ID_SLAVE);
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdSlave).getId());
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }

            try {
                strategyService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                stpTrackingSlaveSteps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
            } catch (Exception e) {
            }
        });
    }

    @Test
    @SneakyThrows
    @AllureId("1847106")
    @DisplayName("С1847106. Разблокировка подписки. Найдено две блокировки - \"minimum-value\" и \"portfolio-initialization\". Все условия для разблокировки выполнены.")
    @Subfeature("Успешные сценарии")
    @Description("Обработка изменений портфелей ведомых")
    void C1847106() {
        //Добавляем стратегию мастеру
        stpTrackingApiSteps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = stpTrackingSlaveSteps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2.0", date, 2,
            stpTrackingSlaveSteps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        stpTrackingSlaveSteps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //добавляем запись MasterPortfolioValue
        createMasterPortfolioValue(35, strategyId, new BigDecimal(4231), new BigDecimal(9694.45));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        stpTrackingSlaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefoult, null);
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION, periodDefoult, null);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = stpTrackingSlaveSteps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "3", date);
        String baseMoneySl = "8893.36";
        stpTrackingSlaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            baseMoneySl, date, createListSlaveOnePos);
        SlavePortfolioOuterClass.SlavePortfolio command = createCommandSlavePortfolio(contractIdSlave, 2, 3, 2, 511070);
        //Форимируем и отправляем событие
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из топика tracking.subscription.event & tracking.delay.command
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_SUBSCRIPTION_EVENT);
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_DELAY_COMMAND);
        stringToByteSenderService.send(TRACKING_SLAVE_PORTFOLIO, command.getContractId(), eventBytes);

        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofNanos(200)).pollInterval(Duration.ofNanos(200))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getBlocked(), equalTo(false));

        checkSubscription(contractIdSlave, SubscriptionStatus.active,  false, null);
        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION.getAlias()).getPeriod().lower().toString();
        checkSubscriptionBlock(contractIdSlave, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION, getLowerPeriod, "now()");
        checkSubscriptionBlock(contractIdSlave, SubscriptionBlockReason.MINIMUM_VALUE, getLowerPeriod, "now()");

        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiption = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));

        Tracking.Event messageForSubscription = filterMessageByKey(messagesFromTrackingSubsctiption, contractIdSlave);
        checkMessageFromSubscriptionEvent(messageForSubscription, contractIdSlave, "UPDATED", subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION);

        Tracking.PortfolioCommand getFirstDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommand, contractIdSlave);
        checkMessageFromDelayCommand(getFirstDelayedMessage, contractIdSlave, "SYNCHRONIZE","now()");
    }


    @Test
    @SneakyThrows
    @AllureId("1846957")
    @DisplayName("C1846957. Разблокировка подписки. Найдена блокировка \"portfolio-initialization\". Все условия для разблокировки выполнены.")
    @Subfeature("Успешные сценарии")
    @Description("Обработка изменений портфелей ведомых")
    void C1846957() {
        //Добавляем стратегию мастеру
        stpTrackingApiSteps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = stpTrackingSlaveSteps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2.0", date, 2,
            stpTrackingSlaveSteps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        stpTrackingSlaveSteps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //добавляем запись MasterPortfolioValue
        createMasterPortfolioValue(35, strategyId, new BigDecimal(4231), new BigDecimal(9694.45));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        stpTrackingSlaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION, periodDefoult, null);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = stpTrackingSlaveSteps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "3", date);
        String baseMoneySl = "8893.36";
        stpTrackingSlaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            baseMoneySl, date, createListSlaveOnePos);
        SlavePortfolioOuterClass.SlavePortfolio command = createCommandSlavePortfolio(contractIdSlave, 2, 3, 2, 2122);
        //Форимируем и отправляем событие
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из топика tracking.subscription.event & tracking.delay.command
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_SUBSCRIPTION_EVENT);
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_DELAY_COMMAND);
        stringToByteSenderService.send(TRACKING_SLAVE_PORTFOLIO, command.getContractId(), eventBytes);

        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofNanos(200)).pollInterval(Duration.ofNanos(200))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getBlocked(), equalTo(false));

        checkSubscription(contractIdSlave, SubscriptionStatus.active,  false, null);
        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION.getAlias()).getPeriod().lower().toString();
        checkSubscriptionBlock(contractIdSlave, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION, getLowerPeriod, "now()");

        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiption = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));

        Tracking.Event messageForSubscription = filterMessageByKey(messagesFromTrackingSubsctiption, contractIdSlave);
        checkMessageFromSubscriptionEvent(messageForSubscription, contractIdSlave, "UPDATED", subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION);

        Tracking.PortfolioCommand getFirstDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommand, contractIdSlave);
        checkMessageFromDelayCommand(getFirstDelayedMessage, contractIdSlave, "SYNCHRONIZE","now()");
    }


    @Test
    @SneakyThrows
    @AllureId("1845447")
    @DisplayName("C1845447. Разблокировка подписки. Найдена блокировка \"minimum-value\". Все условия для разблокировки выполнены.")
    @Subfeature("Успешные сценарии")
    @Description("Обработка изменений портфелей ведомых")
    void C1845447() {
        //Добавляем стратегию мастеру
        stpTrackingApiSteps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = stpTrackingSlaveSteps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2.0", date, 2,
            stpTrackingSlaveSteps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        stpTrackingSlaveSteps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //добавляем запись MasterPortfolioValue
        createMasterPortfolioValue(35, strategyId, new BigDecimal(4231), new BigDecimal(9694.45));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        stpTrackingSlaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefoult, null);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = stpTrackingSlaveSteps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "3", date);
        String baseMoneySl = "8893.36";
        stpTrackingSlaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            baseMoneySl, date, createListSlaveOnePos);
        SlavePortfolioOuterClass.SlavePortfolio command = createCommandSlavePortfolio(contractIdSlave, 2, 3, 2, 423201);
        //Форимируем и отправляем событие
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из топика tracking.subscription.event & tracking.delay.command
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_SUBSCRIPTION_EVENT);
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_DELAY_COMMAND);
        stringToByteSenderService.send(TRACKING_SLAVE_PORTFOLIO, command.getContractId(), eventBytes);

        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofNanos(200)).pollInterval(Duration.ofNanos(200))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getBlocked(), equalTo(false));

        checkSubscription(contractIdSlave, SubscriptionStatus.active,  false, null);
        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE.getAlias()).getPeriod().lower().toString();
        checkSubscriptionBlock(contractIdSlave, SubscriptionBlockReason.MINIMUM_VALUE, getLowerPeriod, "now()");

        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiption = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));

        Tracking.Event messageForSubscription = filterMessageByKey(messagesFromTrackingSubsctiption, contractIdSlave);
        checkMessageFromSubscriptionEvent(messageForSubscription, contractIdSlave, "UPDATED", subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE);

        Tracking.PortfolioCommand getFirstDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommand, contractIdSlave);
        checkMessageFromDelayCommand(getFirstDelayedMessage, contractIdSlave, "SYNCHRONIZE","now()");
    }


    @Test
    @SneakyThrows
    @AllureId("1848857")
    @DisplayName("C1848857. Разблокировка подписки. Найдено две блокировки - \"minimum-value\" и \"portfolio-initialization\". Не выполнено условие для разблокировки \"minimum_value\".")
    @Subfeature("Успешные сценарии")
    @Description("Обработка изменений портфелей ведомых")
    void C1848857() {
        //Добавляем стратегию мастеру
        stpTrackingApiSteps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = stpTrackingSlaveSteps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,"2.0", date, 2,
            stpTrackingSlaveSteps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        stpTrackingSlaveSteps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //добавляем запись MasterPortfolioValue
        createMasterPortfolioValue(35, strategyId, new BigDecimal(4231), new BigDecimal(9694.45));
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        stpTrackingSlaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.MINIMUM_VALUE, periodDefoult, null);
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION, periodDefoult, null);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = stpTrackingSlaveSteps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "3", date);
        String baseMoneySl = "8893.36";
        stpTrackingSlaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            baseMoneySl, date, createListSlaveOnePos);
        SlavePortfolioOuterClass.SlavePortfolio command = createCommandSlavePortfolio(contractIdSlave, 2, 3, 2, 423051);
        //Форимируем и отправляем событие
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из топика tracking.subscription.event & tracking.delay.command
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_SUBSCRIPTION_EVENT);
        stpTrackingApiSteps.resetOffsetToEnd(TRACKING_DELAY_COMMAND);
        stringToByteSenderService.send(TRACKING_SLAVE_PORTFOLIO, command.getContractId(), eventBytes);

        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofNanos(200)).pollInterval(Duration.ofNanos(200))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getBlocked(), equalTo(true));

        checkSubscription(contractIdSlave, SubscriptionStatus.active,  true, null);
        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION.getAlias()).getPeriod().lower().toString();
        checkSubscriptionBlock(contractIdSlave, SubscriptionBlockReason.PORTFOLIO_INITIALIZATION, getLowerPeriod, "now()");
        checkSubscriptionBlock(contractIdSlave, SubscriptionBlockReason.MINIMUM_VALUE, getLowerPeriod, null);

        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiption = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));

        assertThat("Отправили событие в топик TRACKING_SUBSCRIPTION_EVENT", messagesFromTrackingSubsctiption.size(), equalTo(0));
        assertThat("Отправили событие в топик TRACKING_DELAY_COMMAND", messagesFromDelayCommand.size(), equalTo(0));
    }


    //методы для работы тестов**********************************************************************
    @SneakyThrows
    void createMasterPortfolioValue(int minusDays, UUID strategyId,
                                    BigDecimal minimumValue, BigDecimal value) {

        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(minusDays).toInstant()))
            .minimumValue(minimumValue)
            .value(value)
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }

    @Step("Формируем команду в формате Protobuf в соответствии со схемой tracking-slave-portfolio.proto: ")
    SlavePortfolioOuterClass.SlavePortfolio createCommandSlavePortfolio (String contractIdSlave, int version,
                                                                         int comparedToMasterVersion,
                                                                         int scaleValue, long unscaledValue) {

        return SlavePortfolioOuterClass.SlavePortfolio.newBuilder()
            .setContractId(contractIdSlave)
            .setStrategyId(utilsTest.buildByteString(strategyId))
            .setVersion(version)
            .setComparedToMasterVersion(comparedToMasterVersion)
            .setValue(SlavePortfolioOuterClass.Decimal.newBuilder()
                .setScale(scaleValue)
                .setUnscaled(unscaledValue)
                .build())
            .build();
    }

    //проверяем данные в subscription
    void checkSubscription (String contractId, SubscriptionStatus status, Boolean blocked, java.sql.Timestamp endTime) {
        Subscription getSubscription = subscriptionService.getSubscriptionByContract(contractId);
        assertThat("status != " + status, getSubscription.getStatus(), equalTo(status));
        assertThat("end_time !=" + endTime, getSubscription.getEndTime(), equalTo(endTime));
        assertThat("blocked != " + blocked, getSubscription.getBlocked(), equalTo(blocked));
    }

    //проверяем блокировку подписки
    void checkSubscriptionBlock (String contractId, SubscriptionBlockReason subscriptionBlockReason, String lowerPeriod, String upperPeriod) {
//        Date dateNowOne = new Date(System.currentTimeMillis());
//        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
//        String dateNow = (formatter.format(dateNowOne));
        ZonedDateTime zonedDateTime = Instant.now().atZone(ZoneId.of("UTC+03:00"));
        DateTimeFormatter formatterInstance = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String formattedString = zonedDateTime.format(formatterInstance);
        SubscriptionBlock getDataFromSubscriptionBlock =  subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractId).getId(), subscriptionBlockReason.getAlias());
        assertThat("lower(period) !=  now()" , getDataFromSubscriptionBlock.getPeriod().lower().toString(), equalTo(lowerPeriod));
        //assertThat("upper(period) !=  " + dateNow, getDataFromSubscriptionBlock.getPeriod().upper().toString().substring(0, 16), equalTo(dateNow));
        if (upperPeriod != null){
            assertThat("upper(period) !=  " + formattedString, getDataFromSubscriptionBlock.getPeriod().upper().toString().substring(0, 16), equalTo(formattedString));
        }
        else {
            assertThat("upper(period) !=  null", getDataFromSubscriptionBlock.getPeriod().upper(), equalTo(null));
        }
        assertThat("subscriptionBlockReason != " + subscriptionBlockReason.getAlias() , getDataFromSubscriptionBlock.getReason(), equalTo(subscriptionBlockReason.getAlias()));
    }

    //проверяем параметры команды по синхронизации
    void checkMessageFromSubscriptionEvent (Tracking.Event registrationMessage, String contractId, String action, Long subscriptionId, SubscriptionBlockReason subscriptionBlockReason) {
        SubscriptionBlock subscriptionBlock = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId, subscriptionBlockReason.getAlias());
        assertThat("action не равен " + action, registrationMessage.getAction().toString(), is(action));
        UUID getStrategyId =  utilsTest.getGuidFromByteArray(registrationMessage.getSubscription().getStrategy().getId().toByteArray());
        assertThat("strategyId не равен " + strategyId, getStrategyId, is(strategyId));
        assertThat("title не равен " + title, registrationMessage.getSubscription().getStrategy().getTitle(), is(title));
        assertThat("ID контракта не равен " + contractId, registrationMessage.getSubscription().getContractId(), is(contractId));
        assertThat("subscriptionId не равно " + subscriptionId, registrationMessage.getSubscription().getId(), is(subscriptionId));
        assertThat("subscription.block.id не равно " + subscriptionBlock.getId(), registrationMessage.getSubscription().getBlock().getId(), is(subscriptionBlock.getId()));
        assertThat("subscription.block.period.started_at не равно " + subscriptionBlock.getPeriod().lower(), registrationMessage.getSubscription().getBlock().getPeriod().getStartedAt().getSeconds(), is(((LocalDateTime) subscriptionBlock.getPeriod().lower()).toEpochSecond(ZoneOffset.ofHours(3))));
        assertThat("subscription.block.period.ended_at не равно " + subscriptionBlock.getPeriod().upper(), registrationMessage.getSubscription().getBlock().getPeriod().getEndedAt().getSeconds(), is(((LocalDateTime) subscriptionBlock.getPeriod().upper()).toEpochSecond(ZoneOffset.ofHours(3))));
    }

    //проверяем параметры команды по синхронизации
    void checkMessageFromDelayCommand (Tracking.PortfolioCommand registrationMessage, String contractId, String operation, String createdAt) {
        assertThat("ID договора ведомого не равен " + contractId, registrationMessage.getContractId(), is(contractId));
        assertThat("operation не равен " + operation, registrationMessage.getOperation().toString(), is(operation));
    }

    //проверяем параметры команды по подписке
    @SneakyThrows
    Tracking.Event filterMessageByKey (List<Pair<String, byte[]>> messages, String contractId) {
        Pair<String, byte[]> message = messages.stream()
            .filter(ms ->  ms.getKey().equals(contractId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event parsedMessage = Tracking.Event.parseFrom(message.getValue());
        log.info("Cобытие  в  racking.subscription.event:  {}", parsedMessage);
        return parsedMessage;
    }

    //проверяем параметры команды по синхронизации
    @SneakyThrows
    Tracking.PortfolioCommand filterMessageByKeyForDelayCommand (List<Pair<String, byte[]>> messages, String contractId) {
        Pair<String, byte[]> message = messages.stream()
            .filter(ms ->  ms.getKey().equals(contractId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand parsedMessage = Tracking.PortfolioCommand.parseFrom(message.getValue());
        log.info("Cобытие  в  racking.subscription.event:  {}", parsedMessage);
        return parsedMessage;
    }

}
