package stpTrackingMaster.handleActualizeCommand;


import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.tracking.master.TrackingMaster;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию виртуального портфеля")
@Feature("TAP-8055")
@DisplayName("stp-tracking-master")
@Tags({@Tag("stp-tracking-master"), @Tag("handleActualizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingMasterStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
})
public class HandleActualizeCommandTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingMasterSteps steps;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpInstrument instrument;

    UtilsTest utilsTest = new UtilsTest();



    MasterPortfolio masterPortfolio;
    MasterSignal masterSignal;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    int version;
    String siebelIdMaster;
    String siebelIdSlave;
    String siebelIdSlaveActive;
    String siebelIdSlaveBlocked;
    Double dynamicLimitQuantity = 100d;
    String title;
    String description;
    UUID strategyId;
    UUID investIdMaster;
    UUID investIdSlaveActiveSubscription;
    String contractIdSlaveActiveSubscription;
    UUID investIdSlaveBlockedSubscription;
    String contractIdSlaveBlockedSubscription;
    UUID investIdSlave;


    @BeforeAll
    void getdataFromInvestmentAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
        siebelIdSlave = stpSiebel.siebelIdSlaveStpTrackingMaster;
        siebelIdSlaveActive = stpSiebel.siebelIdSlaveActiveStpTrackingMaster;
        siebelIdSlaveBlocked = stpSiebel.siebelIdSlaveBlockedStpTrackingMaster;
        int randomNumber = 0 + (int) (Math.random() * 100);
        title = "Autotest " + String.valueOf(randomNumber);
        description = "autotest HandleActualizeCommandTest for Master";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slaveActiveSubscription в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlaveActive = steps.getBrokerAccounts(siebelIdSlaveActive);
        investIdSlaveActiveSubscription = resAccountSlaveActive.getInvestId();
        contractIdSlaveActiveSubscription = resAccountSlaveActive.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slaveBlockedSubscription в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlaveBlockedSubscription = steps.getBrokerAccounts(siebelIdSlaveBlocked);
        investIdSlaveBlockedSubscription = resAccountSlaveBlockedSubscription.getInvestId();
        contractIdSlaveBlockedSubscription = resAccountSlaveBlockedSubscription.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelIdSlave);
        steps.deleteDataFromDb(siebelIdMaster);
        steps.deleteDataFromDb(siebelIdSlaveActive);
        steps.deleteDataFromDb(siebelIdSlaveBlocked);
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
                trackingService.deleteStrategy(steps.strategyMaster);
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
                masterSignalDao.deleteMasterSignal(strategyId, version);
            } catch (Exception e) {
            }
//            try {
//                steps.createEventInTrackingEvent(contractIdSlave);
//            } catch (Exception e) {
//            }
        });
    }

    @SneakyThrows
    @Test
    @AllureId("662388")
    @DisplayName("C662388.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1.Buy")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662388() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal price = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(10).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, price,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, dynamicLimitQuantity, tailOrderQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        checkParamCommand(portfolioCommand, contractIdSlave, "SYNCHRONIZE", key);
        // проверяем портфель мастера
        checkParamMasterPortfolio(baseMoney, createAt, instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, quantityPos, date, versionPos,
            "12", "10");
    }


    @SneakyThrows
    @Test
    @AllureId("719882")
    @DisplayName("C719882.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        "Не найдена запись в master_signal")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C719882() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal price = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(0)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommandWithOutTailOrderQuantity(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, price,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096,utilsTest.buildByteString(instrument.instrumentId2XS0587031096));
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        checkParamCommand(portfolioCommand, contractIdSlave, "SYNCHRONIZE", key);
        // проверяем портфель мастера
        checkParamMasterPortfolio(baseMoney, createAt, instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, quantityPos, date, versionPos,
            "12", "10");
        checkParamMasterSignal(now, price, quantityS, 0d, tailOrderQuantity);
    }


    @SneakyThrows
    @Test
    @AllureId("720132")
    @DisplayName("C720132.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        "Найдена запись в master_signal, state = null")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C720132() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем запись о сигнале
        Byte action = (byte) 12;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyId, version, action, date, price, quantity, null, instrument.tickerXS0587031096,
            instrument.tradingClearingAccountXS0587031096, new BigDecimal("200"), dynamicLimitQuantity);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, priceS,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, dynamicLimitQuantity, tailOrderQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        checkParamCommand(portfolioCommand, contractIdSlave, "SYNCHRONIZE", key);
        // проверяем портфель мастера
        checkParamMasterPortfolio(baseMoney, createAt, instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, quantityPos, date, versionPos,
            "12", "10");
        OffsetDateTime offsetDateTime = date.toInstant().atOffset(ZoneOffset.UTC);
        checkParamMasterSignal(offsetDateTime, priceS, quantityS, dynamicLimitQuantity, tailOrderQuantity);
    }


    @SneakyThrows
    @Test
    @AllureId("663357")
    @DisplayName("C663357.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1, не передан base_money_position")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C663357() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommandNoBaseMoney(contractIdMaster, now, version,
            10, 0, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        checkParamCommand(portfolioCommand, contractIdSlave, "SYNCHRONIZE", key);
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<MasterPortfolio.Position> positionXS0587031096 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0587031096))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionMTS0620 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerMTS0620))
            .collect(Collectors.toList());
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneyPortfolio));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", positionMTS0620.get(0).getTicker(), is(instrument.tickerMTS0620));
        assertThat("tradingClearingAccountPos позиции не равен", positionMTS0620.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountMTS0620));
        assertThat("quantity позиции не равен", positionMTS0620.get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", positionMTS0620.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", positionMTS0620.get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", positionXS0587031096.get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", positionXS0587031096.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("quantity позиции не равен", positionXS0587031096.get(0).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", positionXS0587031096.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", positionXS0587031096.get(0).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", positionXS0587031096.get(0).getLastChangeAction().toString(), is("12"));
    }


    private static Stream<Arguments> strategyStatus() {
        return Stream.of(
            Arguments.of(StrategyStatus.draft),
            Arguments.of(StrategyStatus.frozen)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("strategyStatus")
    @AllureId("663495")
    @DisplayName("C663495.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1, strategy.status = 'draft' / 'frozen'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C663495(StrategyStatus strategyStatus) {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime nowForFrozzen = null;
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        if (strategyStatus.equals(StrategyStatus.frozen)) {
            nowForFrozzen = LocalDateTime.now();
        }
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            strategyStatus, 0, nowForFrozzen);
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(10).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, priceS,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, dynamicLimitQuantity, tailOrderQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //проверяем портфель мастера
        await().atMost(TEN_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId), notNullValue());
        List<MasterPortfolio.Position> positionXS0587031096 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0587031096))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionMTS0620 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerMTS0620))
            .collect(Collectors.toList());
        UUID positionIdXS0587031096 = UtilsTest.getGuidFromByteArray(command.getPortfolio().getPosition(0).getPositionId().toByteArray());
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney.toString()));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", positionMTS0620.get(0).getTicker(), is(instrument.tickerMTS0620));
        assertThat("tradingClearingAccountPos позиции не равен", positionMTS0620.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountMTS0620));
        assertThat("quantity позиции не равен", positionMTS0620.get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", positionMTS0620.get(0).getChangedAt().toInstant(), is(date.toInstant()));
        assertThat("last_change_detected_version позиции не равен", positionMTS0620.get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", positionMTS0620.get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", positionXS0587031096.get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", positionXS0587031096.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("position_id не равен", positionXS0587031096.get(0).getPositionId(), is(positionIdXS0587031096));
        assertThat("quantity позиции не равен", positionXS0587031096.get(0).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", positionXS0587031096.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", positionXS0587031096.get(0).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", positionXS0587031096.get(0).getLastChangeAction().toString(), is("12"));
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("12"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(priceS.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("666901")
    @DisplayName("C666901.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1.Sell")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C666901() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        String quantityPos = "1";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, quantityPos, positionAction, version, version,
            baseMoneyPortfolio, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(10).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version + 1, 0, 0,
            50000, 1, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, priceS,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, dynamicLimitQuantity, tailOrderQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //проверяем параметры команды по синхронизации
        checkParamCommand(portfolioCommand, contractIdSlave, "SYNCHRONIZE", key);
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version + 1));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5000.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("0"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(version + 1));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("11"));
        version = version + 1;
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("11"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(priceS.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }

    @SneakyThrows
    @Test
    @AllureId("1244154")
    @DisplayName("C1244154.HandleActualizeCommand.SaveSignal.Игнорируем заблокированные подписки")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1244154() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем  активную подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlaveActiveSubscription, null, contractIdSlaveActiveSubscription, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //Создаем заблокированную подписку
        steps.createSubcription(UUID.randomUUID(), null, contractIdSlaveBlockedSubscription, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, true, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal price = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(10).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, price,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, dynamicLimitQuantity, tailOrderQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(20));
        //Проверяем, что получили 1 событие
        assertThat("Получили больше 1 события", messages.size(), equalTo(1));
        //Смотрим, сообщение, которое поймали в топике kafka
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        checkParamCommand(portfolioCommand, contractIdSlaveActiveSubscription, "SYNCHRONIZE", key);
        // проверяем портфель мастера
        checkParamMasterPortfolio(baseMoney, createAt, instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, quantityPos, date, versionPos,
            "12", "10");
        //Удаляем данные
        try {
            subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlaveActiveSubscription));
            subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlaveBlockedSubscription));
        } catch (Exception e) {
        }
        try {
            contractService.deleteContract(contractService.getContract(contractIdSlaveActiveSubscription));
            contractService.deleteContract(contractService.getContract(contractIdSlaveBlockedSubscription));
        } catch (Exception e) {
        }
        try {
            clientService.deleteClient(clientService.getClient(investIdSlaveActiveSubscription));
            clientService.deleteClient(clientService.getClient(investIdSlaveBlockedSubscription));
        } catch (Exception e) {
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1324954")
    @DisplayName("C1324954.HandleActualizeCommand.SaveSignal.Поле tail_order_quantity. Не передаём параметр")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1324954() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String baseMoney = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        MasterPortfolio.BaseMoneyPosition baseMoneyPortfolio = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(baseMoney))
            .changedAt(date)
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPortfolio, positionList);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceSignal = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantitySignal = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommandWithOutTailOrderQuantity(contractIdMaster, now, version + 1,
            10, 0, 49900, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE,
            priceSignal, quantitySignal, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, utilsTest.buildByteString(instrument.instrumentId2XS0587031096));
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        // проверяем, что обновился state = 1
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).until(() ->
            masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1);
        assertThat("Состояние сигнала  не равно", masterSignal.getTailOrderQuantity().toString(), is("0"));
    }


    @SneakyThrows
    @Test
    @AllureId("1324919")
    @DisplayName("C1324919.HandleActualizeCommand.SaveSignal.Поле tail_order_quantity. Передаём параметр в сообщении")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1324919() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String baseMoney = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        MasterPortfolio.BaseMoneyPosition baseMoneyPortfolio = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(baseMoney))
            .changedAt(date)
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPortfolio, positionList);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceSignal = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantitySignal = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(10).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version + 1,
            10, 0, 49900, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE,
            priceSignal, quantitySignal, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, dynamicLimitQuantity, tailOrderQuantity);
        log.info("Команда в tracking.master.command:  {}", command);

        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        // проверяем, поле tail_order_quantity
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).until(() ->
            masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1);
        assertThat("Состояние сигнала  не равно", masterSignal.getTailOrderQuantity().toString(), is("10"));
    }


    @SneakyThrows
    @Test
    @AllureId("1992650")
    @DisplayName("1992650 HandleActualizeCommand.SaveSignal.Сохраняем переданный position_id. Позиция есть в портфеле")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1992650() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal price = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity = Tracking.Decimal.newBuilder()
            .setUnscaled(0)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommandWithOutTailOrderQuantity(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, price,
            quantityS, instrument.tickerXS0587031096, instrument.tradingClearingAccountXS0587031096, instrument.classCodeXS0587031096,utilsTest.buildByteString(instrument.instrumentId2XS0587031096));
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);

        //Проверяем событие из топика tracking.master.portfolio.operation
        List<Pair<String, byte[]>> messagesFromMasterPortfolioOperation = kafkaReceiver.receiveBatch(MASTER_PORTFOLIO_OPERATION, Duration.ofSeconds(20));
        Pair<String, byte[]> messageFromMasterPortfolioOperation = messagesFromMasterPortfolioOperation.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        TrackingMaster.MasterPortfolioOperation eventFromMasterPortfolioOperation = TrackingMaster.MasterPortfolioOperation.parseFrom(messageFromMasterPortfolioOperation.getValue());

        checkFirstDividendEventParam(eventFromMasterPortfolioOperation, strategyId, version, instrument.instrumentId2XS0587031096, new BigDecimal("256"), "BUY");

        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        UUID positionId = UtilsTest.getGuidFromByteArray(command.getPortfolio().getPosition(0).getPositionId().toByteArray());
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("4985.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("position_id не равен", masterPortfolio.getPositions().get(0).getPositionId(), is(positionId));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
    }


    /////////***методы для работы тестов**************************************************************************
    //проверяем параметры команды по синхронизации

    void checkFirstDividendEventParam(TrackingMaster.MasterPortfolioOperation masterPortfolioOperation, UUID strategyId, int MasterPortfolioVersion, UUID instrumentId, BigDecimal price, String direction) {
        BigDecimal priceValue = BigDecimal.valueOf(masterPortfolioOperation.getTrade().getPrice().getUnscaled(),
            masterPortfolioOperation.getTrade().getPrice().getScale());
        assertAll(
            () -> assertThat("strategy_id не равен", uuid(masterPortfolioOperation.getStrategyId()), is(strategyId)),
            () -> assertThat("portfolio.version не равен " + MasterPortfolioVersion, masterPortfolioOperation.getPortfolio().getVersion(), is(MasterPortfolioVersion)),
            () -> assertThat("trade.direction != " + direction, masterPortfolioOperation.getTrade().getDirection().toString(), is(direction)),
            () -> assertThat("price != " + price, priceValue, is(price)),
            () -> assertThat("trade.instrument_id не равен " + instrumentId, uuid(masterPortfolioOperation.getTrade().getInstrumentId()), is(instrumentId))

        );
    }

    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    void checkParamCommand(Tracking.PortfolioCommand portfolioCommand, String contractIdSlave, String operation, String key) {
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is(operation));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }

    void checkParamMasterPortfolio(BigDecimal baseMoney, Instant createAt, String tickerPos, String tradingClearingAccountPos,
                                   String quantityPos, Date date, int versionPos, String lastChangeAction, String quantity) {
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //сохраняем в списки значения по позициям в портфеле
        List<MasterPortfolio.Position> positionXS0587031096 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerXS0587031096))
            .collect(Collectors.toList());
        List<MasterPortfolio.Position> positionMTS0620 = masterPortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerMTS0620))
            .collect(Collectors.toList());
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney.toString()));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));

        assertThat("ticker позиции не равен", positionMTS0620.get(0).getTicker(), is(instrument.tickerMTS0620));
        assertThat("tradingClearingAccountPos позиции не равен", positionMTS0620.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountMTS0620));
        assertThat("quantity позиции не равен", positionMTS0620.get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", positionMTS0620.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", positionMTS0620.get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", positionMTS0620.get(0).getLastChangeAction().toString(), is(lastChangeAction));

        assertThat("ticker позиции не равен", positionXS0587031096.get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", positionXS0587031096.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("quantity позиции не равен", positionXS0587031096.get(0).getQuantity().toString(), is(quantity));
        assertThat("ChangedAt позиции не равен", positionXS0587031096.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", positionXS0587031096.get(0).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", positionXS0587031096.get(0).getLastChangeAction().toString(), is(lastChangeAction));
    }


    void checkParamMasterSignal(OffsetDateTime now, Tracking.Decimal price, Tracking.Decimal quantityS, Double dynamicLimitQuantity, Tracking.Decimal tailOrderQuantity) {
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("12"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(price.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
        assertThat("dynamicLimitQuantity  не равно", masterSignal.getDynamicLimitQuantity(), is(dynamicLimitQuantity));
        assertThat("tailOrderQuantity  не равно", masterSignal.getTailOrderQuantity().longValue(), is(tailOrderQuantity.getUnscaled()));
    }


    //создаем портфель master в cassandra с позицией
    void createMasterPortfolioWithPosition(String ticker, String tradingClearingAccount, String classCode, String quantityPos,
                                           Tracking.Portfolio.Position position,
                                           int versionPos, int version, String money, Date date) {
        UUID instrumentUID = steps.getInstrumentUID(ticker, classCode);
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .lastChangeDetectedVersion(versionPos)
            .changedAt(date)
            .quantity(new BigDecimal(quantityPos))
            .positionId(instrumentUID)
            .build());
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPosition, positionList);
    }


    //создаем запись в master_signal
    void createMasterSignal(UUID strategyIdMaster, int version, Byte action, Date createAt, BigDecimal price, BigDecimal quantity,
                            Byte state, String ticker, String tradingClearingAccount, BigDecimal tailOrderQuantity, Double dynamicLimitQuantity) {
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyIdMaster)
            .version(version)
            .action(action)
            .createdAt(createAt)
            .price(price)
            .quantity(quantity)
            .state(state)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .tailOrderQuantity(tailOrderQuantity)
            .dynamicLimitQuantity(dynamicLimitQuantity)
            .build();
        //insert запись в cassandra master_signal
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }


    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandNoBaseMoney(String contractId, OffsetDateTime time, int version,
                                                                                       long unscaled, int scale, Tracking.Decimal price,
                                                                                       Tracking.Decimal quantityS) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerXS0587031096)
            .setTradingClearingAccount(instrument.tradingClearingAccountXS0587031096)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .setQuantity(quantity)
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(price)
            .setQuantity(quantityS)
            .build();
        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .setSignal(signal)
            .build();
        return command;
    }
}
