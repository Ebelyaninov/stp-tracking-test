package stpTrackingMaster.handleActualizeCommand;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
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
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
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
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.Exchange;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию виртуального портфеля")
@Feature("TAP-8055")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-master")
@Tags({@Tag("stp-tracking-master"), @Tag("handleActualizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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


public class HandleActualizeCommandErrorTest {
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    StpTrackingMasterSteps steps;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpInstrument instrument;

    String siebelIdMaster;
    String siebelIdSlave;
    MasterPortfolio masterPortfolio;
    Client clientSlave;
    String contractIdMaster;
    int version;
    UUID strategyId;
    String contractIdSlave;
    UUID investIdMaster;
    String title;
    String description;
    UUID investIdSlave;
    Double dynamicLimitQuantity = 100d;


    @BeforeAll
    void getdataFromInvestmentAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
        siebelIdSlave = stpSiebel.siebelIdSlaveStpTrackingMaster;
        int randomNumber = 0 + (int) (Math.random() * 100);
        title = "Autotest " +String.valueOf(randomNumber);
        description = "new test стратегия autotest";
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelIdSlave);
        steps.deleteDataFromDb(siebelIdMaster);
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

        });
    }

    @SneakyThrows
    @Test
    @AllureId("662316")
    @DisplayName("C662316.HandleActualizeCommand.Стратегия не найдена по contractId")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662316() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity= Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, 4, 12, 0,
            49900, 1, priceS, quantityS, tailOrderQuantity, dynamicLimitQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        Optional<MasterPortfolio> portfolio = masterPortfolioDao.findLatestMasterPortfolio(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("662385")
    @DisplayName("C662385.HandleActualizeCommand.SaveSignal.Не найден текущий портфель master'а в бд Cassandra по contract_id + strategy")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662385() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity= Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, 4, 12, 0,
            49900, 1, priceS, quantityS, tailOrderQuantity, dynamicLimitQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        Optional<MasterPortfolio> portfolio = masterPortfolioDao.findLatestMasterPortfolio(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }



    private static Stream<Arguments> versionParams() {
        return Stream.of(
            Arguments.of(3, 2),
            Arguments.of(3, 3)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("versionParams")
    @AllureId("1614492")
    @DisplayName("HandleActualizeCommand.SaveSignal.Version из команды <= master_portfolio.version")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1614492(int versionPortfolio, int versionCommand ) {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(versionPortfolio, "5000.0", "5.0", date);
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity= Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, versionCommand, 12,
            0, 49900, 1, priceS, quantityS, tailOrderQuantity, dynamicLimitQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(versionPortfolio));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("5.0"));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5000.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("662387")
    @DisplayName("C662387.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля > 1")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662387() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        int versionPortfolio = 3;
        int versionCommand = 5;
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(versionPortfolio, "5000.0", "5.0", date);
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity= Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, versionCommand, 12,
            0, 49900, 1, priceS, quantityS, tailOrderQuantity, dynamicLimitQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(versionPortfolio));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(instrument.tickerXS0587031096));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountXS0587031096));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("5.0"));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5000.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    @SneakyThrows
    @Test
    @AllureId("719222")
    @DisplayName("C719222.HandleActualizeCommand.Валидация команды на актуализацию. В массиве portfolio.position содержится только один элемент")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C719222() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        String key = null;
        int version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(version, "5000.0", "5.0", date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),null);
        //вычитываем все события из tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //формируем команду на актуализацию по ведущему
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommandValid(contractIdMaster, now, version,
            10, 0, 49900, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


    @SneakyThrows
    @Test
    @AllureId("720454")
    @DisplayName("C720454.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        "Найдена запись в master_signal, данные найденного сигнала в master_signal не совпадают с данными команды")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C720454() {
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
        //создаем запись о сигнале
        Byte action = (byte) 11;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyId, version, action, date, price, quantity, null,instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, new BigDecimal("200"), dynamicLimitQuantity);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity= Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE,
            priceS, quantityS, tailOrderQuantity, dynamicLimitQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


    @SneakyThrows
    @Test
    @AllureId("721195")
    @DisplayName("C721195.HandleActualizeCommand.SaveSignal.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        " master_signal.state заполнен")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C721195() {
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
        createMasterPortfolioWithPosition(instrument.tickerMTS0620, instrument.tradingClearingAccountMTS0620, instrument.classCodeMTS0620,
            quantityPos, positionAction, versionPos, versionPortfolio, baseMoneyPortfolio, date);
        //создаем запись о сигнале
        Byte action = (byte) 12;
        Byte state = (byte) 1;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyId, version, action, date, price, quantity, state, instrument.tickerXS0587031096,
            instrument.tradingClearingAccountXS0587031096,  new BigDecimal("200"), dynamicLimitQuantity);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),null);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.Decimal tailOrderQuantity= Tracking.Decimal.newBuilder()
            .setUnscaled(200)
            .setScale(0)
            .build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE,
            priceS, quantityS, tailOrderQuantity, dynamicLimitQuantity);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }

    @SneakyThrows
    @Test
    @AllureId("1858650")
    @DisplayName("C1858650.HandleActualizeCommand.Валидация команды на актуализацию.Заполнен элемент signal, объект portfolio не заполнен")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1858650() {
        Double dynamicLimitQuantity = 100d;
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime time = OffsetDateTime.now();
        String key = null;
        int version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(version, "5000.0", "5.0", date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),null);
        //вычитываем все события из tracking.slave.command
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //формируем команду на актуализацию по ведущему формируем Signal, но не формируем Portfolio
        Tracking.PortfolioCommand command;
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(10)
                .setScale(0)
                .build())
            .setPrice(Tracking.Decimal.newBuilder()
                .setUnscaled(100)
                .setScale(0)
                .build())
            .setTailOrderQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(200)
                .setScale(0)
                .build())
            .setDynamicLimitQuantity(com.google.protobuf.DoubleValue.newBuilder()
                .setValue(dynamicLimitQuantity).build())
            .build();

        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdMaster)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setSignal(signal)
            .build();
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


/////////***методы для работы тестов**************************************************************************

    // создаем команду в топик кафка tracking.master.command
    @Step("Создаем команду в топик кафка tracking.master.command Operation.ACTUALIZE")
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, int version,
                                                                            long unscaled, int scale, long unscaledBaseMoney,
                                                                            int scaleBaseMoney, Tracking.Decimal priceS,
                                                                            Tracking.Decimal quantityS, Tracking.Decimal tailOrderQuantity,
                                                                            Double dynamicLimitQuantity) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerXS0587031096)
            .setTradingClearingAccount(instrument.tradingClearingAccountXS0587031096)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .setQuantity(quantity)
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(priceS)
            .setQuantity(quantityS)
            .setTailOrderQuantity(tailOrderQuantity)
            .setDynamicLimitQuantity(com.google.protobuf.DoubleValue.newBuilder()
                .setValue(dynamicLimitQuantity).build())
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


    void createMasterPortfolio(int version, String money, String quantityPos, Date date) {
        //создаем портфель master в cassandra
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerXS0587031096)
            .tradingClearingAccount(instrument.tradingClearingAccountXS0587031096)
            .quantity(new BigDecimal(quantityPos))
            .build());
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }


    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandValid(String contractId, OffsetDateTime time, int version,
                                                                                 long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                                 Tracking.Portfolio.Action action) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position positionOne = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerXS0587031096)
            .setTradingClearingAccount(instrument.tradingClearingAccountXS0587031096)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .build();
        Tracking.Portfolio.Position positionTwo = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerAAPL)
            .setTradingClearingAccount(instrument.tradingClearingAccountAAPL)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(10)
                .setScale(0)
                .build())
            .setPrice(Tracking.Decimal.newBuilder()
                .setUnscaled(100)
                .setScale(0)
                .build())
            .setTailOrderQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(200)
                .setScale(0)
                .build())
            .setDynamicLimitQuantity(com.google.protobuf.DoubleValue.newBuilder()
                .setValue(100d).build())
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(positionOne)
            .addPosition(positionTwo)
            .setBaseMoneyPosition(baseMoneyPosition)
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
    @Step("Создаем команду в топик кафка tracking.master.command Operation.ACTUALIZE)")
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, int version,
                                                                            long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                            Tracking.Portfolio.Action action, Tracking.Decimal price,
                                                                            Tracking.Decimal quantityS, Tracking.Decimal tailOrderQuantity,
                                                                            Double dynamicLimitQuantity) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerXS0587031096)
            .setTradingClearingAccount(instrument.tradingClearingAccountXS0587031096)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(price)
            .setQuantity(quantityS)
            .setTailOrderQuantity(tailOrderQuantity)
            .setDynamicLimitQuantity(com.google.protobuf.DoubleValue.newBuilder()
                .setValue(dynamicLimitQuantity).build())
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
