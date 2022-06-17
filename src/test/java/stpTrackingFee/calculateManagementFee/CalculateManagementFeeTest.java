package stpTrackingFee.calculateManagementFee;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.Context;
import ru.qa.tinkoff.investTracking.entities.ManagementFee;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.ManagementFeeDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.SptTrackingFeeStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingFeeSteps.StpTrackingFeeSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_FEE_CALCULATE_COMMAND;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_FEE_COMMAND;

@Slf4j
@Epic("calculateManagementFee - Расчет комиссии за управление")
@Feature("TAP-9902")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-fee")
@Tags({@Tag("stp-tracking-fee"), @Tag("calculateManagementFee")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SptTrackingFeeStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})
public class CalculateManagementFeeTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingFeeSteps steps;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    ManagementFeeDao managementFeeDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<SubscriptionApi> subscriptionApiCreator;

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ApiClient.Config.apiConfig()).instruments();

    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Subscription subscription;
    ManagementFee managementFee;
    SlavePortfolio slavePortfolio;
    UUID strategyId;
    String siebelIdMaster;
    String siebelIdSlave;
    String quantitySBER = "20";
    String quantitySU29009RMFS6 = "5";
    BigDecimal minPriceIncrement = new BigDecimal("0.001");
    String quantityYNDX = "2";
    String quantityTEST = "2";
    String quantityFXITTEST = "2";
    UUID investIdMaster;
    UUID investIdSlave;

    String description = "new autotest by fee CalculateManagementFeeTest";


    @BeforeAll
    void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingFee;
        siebelIdSlave = stpSiebel.siebelIdSlaveStpTrackingFee;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        step("Удаляем клиента автоследования", () -> {
            try {
                contractService.deleteContractById(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(investIdSlave);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContractById(contractIdMaster);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(investIdMaster);
            } catch (Exception e) {
            }
        });
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                managementFeeDao.deleteManagementFee(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1013342")
    @DisplayName("C1013342.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt <= метки времени от now(), по cron-выражению")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1013342() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "18171.04");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        checkManagementFeeTwo(subscriptionId);
        checkManagementFeeLast(subscriptionId);
    }


    @SneakyThrows
    @Test
    @AllureId("1025019")
    @DisplayName("C1025019.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt = найденный end_time")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1025019() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        checkManagementFeeTwo(subscriptionId);
        checkManagementFeeLastNoBond(subscriptionId, Date.from(endSubTime.toInstant()));
    }


    @SneakyThrows
    @Test
    @AllureId("1025024")
    @DisplayName("C1025024.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = > метки времени от settlement_period_started_at," +
        " endedAt <= метка времени от now(), по cron-выражению")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1025024() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "18171.04");
        createManagemetFee(subscriptionId);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeLast(subscriptionId);
    }


    @SneakyThrows
    @Test
    @AllureId("1025026")
    @DisplayName("C1025026.CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов." +
        "startedAt > метки времени от settlement_period_started_at, endedAt = найденный end_time")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1025026() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        createManagemetFee(subscriptionId);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeLastNoBond(subscriptionId, Date.from(endSubTime.toInstant()));
    }


    @SneakyThrows
    @Test
    @AllureId("1031163")
    @DisplayName("C1031163.CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов." +
        "startedAt > метки времени от settlement_period_started_at, endedAt = найденный end_time")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1031163() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER, instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //создаем записи о расчете комиссии за управление
        createManagemetFee(subscriptionId);
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        byte[] strategyIdByteArray = feeCommand.getSubscription().getStrategy().getId().toByteArray();
        UUID guidFromByteArray = UtilsTest.getGuidFromByteArray(strategyIdByteArray);
        assertThat("subscription.strategy_id не равен", guidFromByteArray, is(strategyId));
        double rateResultScale = Math.pow(10, -1 * feeCommand.getRate().getScale());
        BigDecimal rateResult = BigDecimal.valueOf(feeCommand.getRate().getUnscaled()).multiply(BigDecimal.valueOf(rateResultScale));
        assertThat("rate result не равен", rateResult, is(new BigDecimal("0.04")));
        assertThat("rate unscaled  не равен", feeCommand.getRate().getUnscaled(), is(4L));
        assertThat("rate scale не равен", feeCommand.getRate().getScale(), is(2));
        assertThat("currency не равен", feeCommand.getCurrency().toString(), is("RUB"));
        LocalDateTime commandStartTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getStartedAt().getSeconds()), ZoneId.of("UTC"));
        LocalDateTime commandEndTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getEndedAt().getSeconds()), ZoneId.of("UTC"));
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().minusHours(3).toString()));
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, this.strategyId, subscriptionId, 3);
        BigDecimal portfolioValue = managementFee.getContext().getPortfolioValue();
        double scale = Math.pow(10, -1 * feeCommand.getManagement().getPortfolioValue().getScale());
        BigDecimal value = BigDecimal.valueOf(feeCommand.getManagement().getPortfolioValue().getUnscaled()).multiply(BigDecimal.valueOf(scale));
        assertThat("value стоимости портфеля не равно", value, is(portfolioValue));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("1025033")
    @DisplayName("C1025033.CalculateManagementFee.Расчет комиссии за управление. " +
        "Не найдена запись в materialized view changed_at_slave_portfolio")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1025033() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
//        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        Optional<ManagementFee> portfolioValue = managementFeeDao.findManagementFee(contractIdSlave, strategyId, subscriptionId, 0);
        assertThat("запись по расчету комиссии за управления не равно", portfolioValue.isPresent(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1025013")
    @DisplayName("C1025013.CalculateManagementFee.Расчет комиссии за управление. " +
        "Расчетный период еще не начался.StartedAt > now()")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1025013() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        createManagemetFee(subscriptionId);
        List<Context.Positions> positionListWithPosTwo = new ArrayList<>();
        positionListWithPosTwo.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .price(new BigDecimal("335.04"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(2).toInstant()))
            .build());
        positionListWithPosTwo.add(Context.Positions.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal(quantityYNDX))
            .price(new BigDecimal("4821.4"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(2).toInstant()))
            .build());
        Context contextWithPosTwo = Context.builder()
            .portfolioValue(new BigDecimal("25580.22"))
            .positions(positionListWithPosTwo)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 3,
            Date.from(LocalDate.now().minusDays(1).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().atStartOfDay().toInstant(UTC)), contextWithPosTwo, Date.from(LocalDate.now().atStartOfDay().toInstant(UTC)));
        //вычитываем все события из топика tracking.fee.calculate.command
//        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.calculate.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        // проверяем, что команда в tracking.fee.calculate.command не улетает
        //        Смотрим, сообщение,  в топике kafka tracking.fee.command
        List<Pair<String, byte[]>> messagesFee = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(10));
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


    @SneakyThrows
    @Test
    @AllureId("1418659")
    @DisplayName("C1418659.CalculateManagementFee.Не найдена позиция в exchangePositionCache при расчете стоимости портфеля")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1418659() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40", instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, "25000.0",
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));
        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositionsExchangePositionCache(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, "18171.04",
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, "18700.02",
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        Optional<ManagementFee> portfolio = managementFeeDao.findLatestManagementFee(contractIdSlave, strategyId, subscriptionId, 2);
        assertThat("запись по комисси не равно", portfolio.isPresent(), is(false));
        Optional<ManagementFee> portfolio1 = managementFeeDao.findLatestManagementFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("запись по комисси не равно", portfolio1.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1418733")
    @DisplayName("C1418733.CalculateManagementFee.Не найдена позиция в instrumentPriceCache при расчете стоимости портфеля")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1418733() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, "25000.0",
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));
        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositionsExchangePositionCache(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, "18171.04",
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, "18700.02",
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        Optional<ManagementFee> portfolio = managementFeeDao.findLatestManagementFee(contractIdSlave, strategyId, subscriptionId, 2);
        assertThat("запись по комисси не равно", portfolio.isPresent(), is(false));
        Optional<ManagementFee> portfolio1 = managementFeeDao.findLatestManagementFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("запись по комисси не равно", portfolio1.isPresent(), is(false));

    }


    @SneakyThrows
    @Test
    @AllureId("1487145")
    @DisplayName("C1487145.CalculateManagementFee.CalculateManagementFee.Расчет комиссии за управление." +
        " Определения расчетных периодов.startedAt = subscription.start_time, endedAt <= метки времени от now()," +
        " по cron-выражению. Отрицательное значение по базовой валюте.PortfolioValue < 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1487145() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "-18171.04");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        checkManagementFeeTwo(subscriptionId);
        checkManagementFeeLast(subscriptionId);
    }

    @SneakyThrows
    @Test
    @AllureId("1488500")
    @DisplayName("C1488500.CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов." +
        "startedAt = subscription.start_time, endedAt <= метки времени от now(), по cron-выражению. " +
        "Отрицательное значение по базовой валюте.PortfolioValue < 0.Для bond")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1488500() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolio("25000.0", "-18700.02", "18171.04");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        checkManagementFeeTwo(subscriptionId);
        checkManagementFeeLast(subscriptionId);
    }


    @SneakyThrows
    @Test
    @AllureId("1488428")
    @DisplayName("C1488428.CalculateManagementFee.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt = найденный end_time." +
        "Отрицательное значение по базовой валюте.PortfolioValue > 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1488428() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "-974.42");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOne(subscriptionId);
        checkManagementFeeTwo(subscriptionId);
        checkManagementFeeLastNoBond(subscriptionId, Date.from(endSubTime.toInstant()));
    }


    @SneakyThrows
    @Test
    @AllureId("1488499")
    @DisplayName("C1488499.CalculateManagementFee. CalculateManagementFee.Расчет комиссии за управление." +
        " Определения расчетных периодов.startedAt = subscription.start_time, endedAt <= метки времени от now()," +
        " по cron-выражению.ContractBlocked")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1488499() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, true, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "18171.04");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOneBlocked(subscriptionId, "contractBlocked");
        checkManagementFeeTwoBlocked(subscriptionId, "contractBlocked");
        checkManagementFeeLastBlocked(subscriptionId, "contractBlocked");
    }


    @SneakyThrows
    @Test
    @AllureId("1492239")
    @DisplayName("C1492239.CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов." +
        "startedAt > метки времени от settlement_period_started_at, endedAt = найденный end_time.ContractBlocked")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1492239() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, true, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //создаем записи о расчете комиссии за управление
        createManagemetFee(subscriptionId);
        //вычитываем все события из топика tracking.fee.calculate.command
//        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        byte[] strategyIdByteArray = feeCommand.getSubscription().getStrategy().getId().toByteArray();
        UUID guidFromByteArray = UtilsTest.getGuidFromByteArray(strategyIdByteArray);
        assertThat("subscription.strategy_id не равен", guidFromByteArray, is(strategyId));
        double rateResultScale = Math.pow(10, -1 * feeCommand.getRate().getScale());
        BigDecimal rateResult = BigDecimal.valueOf(feeCommand.getRate().getUnscaled()).multiply(BigDecimal.valueOf(rateResultScale));
        assertThat("rate result не равен", rateResult, is(new BigDecimal("0.04")));
        assertThat("rate unscaled  не равен", feeCommand.getRate().getUnscaled(), is(4L));
        assertThat("rate scale не равен", feeCommand.getRate().getScale(), is(2));
        assertThat("currency не равен", feeCommand.getCurrency().toString(), is("RUB"));
        LocalDateTime commandStartTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getStartedAt().getSeconds()), ZoneId.of("UTC"));
        LocalDateTime commandEndTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getEndedAt().getSeconds()), ZoneId.of("UTC"));
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().minusHours(3).toString()));
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, this.strategyId, subscriptionId, 3);
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is("contractBlocked"));
        double scale = Math.pow(10, -1 * feeCommand.getManagement().getPortfolioValue().getScale());
        BigDecimal value = BigDecimal.valueOf(feeCommand.getManagement().getPortfolioValue().getUnscaled()).multiply(BigDecimal.valueOf(scale));
        assertThat("value стоимости портфеля не равно", value.toString(), is("0.0"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("1492298")
    @DisplayName("C1492298.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt = найденный end_time.SubscriptionBlocked")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1492298() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new Timestamp(endSubTime.toInstant().toEpochMilli()), true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeOneBlocked(subscriptionId, "subscriptionBlocked");
        checkManagementFeeTwoBlocked(subscriptionId, "subscriptionBlocked");
        checkManagementFeeLastBlocked(subscriptionId, "subscriptionBlocked");
    }


    @SneakyThrows
    @Test
    @AllureId("1492301")
    @DisplayName("C1492301.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = > метки времени от settlement_period_started_at," +
        " endedAt <= метка времени от now(), по cron-выражению.SubscriptionBlocked")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1492301() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "18171.04");
        createManagemetFee(subscriptionId);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkManagementFeeLastBlocked(subscriptionId, "subscriptionBlocked");
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        byte[] strategyIdByteArray = feeCommand.getSubscription().getStrategy().getId().toByteArray();
        UUID guidFromByteArray = UtilsTest.getGuidFromByteArray(strategyIdByteArray);
        assertThat("subscription.strategy_id не равен", guidFromByteArray, is(strategyId));
        double rateResultScale = Math.pow(10, -1 * feeCommand.getRate().getScale());
        BigDecimal rateResult = BigDecimal.valueOf(feeCommand.getRate().getUnscaled()).multiply(BigDecimal.valueOf(rateResultScale));
        assertThat("rate result не равен", rateResult, is(new BigDecimal("0.04")));
        assertThat("rate unscaled  не равен", feeCommand.getRate().getUnscaled(), is(4L));
        assertThat("rate scale не равен", feeCommand.getRate().getScale(), is(2));
        assertThat("currency не равен", feeCommand.getCurrency().toString(), is("RUB"));
        LocalDateTime commandStartTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getStartedAt().getSeconds()), ZoneId.of("UTC"));
        LocalDateTime commandEndTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getEndedAt().getSeconds()), ZoneId.of("UTC"));
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().minusHours(3).toString()));
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, this.strategyId, subscriptionId, 3);
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is("subscriptionBlocked"));
        double scale = Math.pow(10, -1 * feeCommand.getManagement().getPortfolioValue().getScale());
        BigDecimal value = BigDecimal.valueOf(feeCommand.getManagement().getPortfolioValue().getUnscaled()).multiply(BigDecimal.valueOf(scale));
        assertThat("value стоимости портфеля не равно", value.toString(), is("0.0"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("1492302")
    @DisplayName("C1492302.CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов." +
        "startedAt > метки времени от settlement_period_started_at, endedAt = найденный end_time")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1492302() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //создаем записи о расчете комиссии за управление
        createManagemetFeeWithBlocked(subscriptionId);
        //вычитываем все события из топика tracking.fee.calculate.command
//        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        byte[] strategyIdByteArray = feeCommand.getSubscription().getStrategy().getId().toByteArray();
        UUID guidFromByteArray = UtilsTest.getGuidFromByteArray(strategyIdByteArray);
        assertThat("subscription.strategy_id не равен", guidFromByteArray, is(strategyId));
        double rateResultScale = Math.pow(10, -1 * feeCommand.getRate().getScale());
        BigDecimal rateResult = BigDecimal.valueOf(feeCommand.getRate().getUnscaled()).multiply(BigDecimal.valueOf(rateResultScale));
        assertThat("rate result не равен", rateResult, is(new BigDecimal("0.04")));
        assertThat("rate unscaled  не равен", feeCommand.getRate().getUnscaled(), is(4L));
        assertThat("rate scale не равен", feeCommand.getRate().getScale(), is(2));
        assertThat("currency не равен", feeCommand.getCurrency().toString(), is("RUB"));
        LocalDateTime commandStartTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getStartedAt().getSeconds()), ZoneId.of("UTC"));
        LocalDateTime commandEndTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getEndedAt().getSeconds()), ZoneId.of("UTC"));
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().minusHours(3).toString()));
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, this.strategyId, subscriptionId, 3);
        BigDecimal portfolioValue = managementFee.getContext().getPortfolioValue();
        double scale = Math.pow(10, -1 * feeCommand.getManagement().getPortfolioValue().getScale());
        BigDecimal value = BigDecimal.valueOf(feeCommand.getManagement().getPortfolioValue().getUnscaled()).multiply(BigDecimal.valueOf(scale));
        assertThat("value стоимости портфеля не равно", value, is(portfolioValue));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }

    @SneakyThrows
    @Test
    @AllureId("1593104")
    @DisplayName("1593104 CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов.ContractBlocked, Slave_portfolio = null")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1593104() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "40",
            instrument.tickerFB, instrument.tradingClearingAccountFB, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, true, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //проверяем полученное событие в tracking.fee.calculate.command
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("portfolioValue не равен", feeCommand.getManagement().getPortfolioValue().getScale(), is(0));
        //проверяем запись в таблице management_fee
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 0);
        assertThat("contractID не равен", managementFee.getContractId(), is(contractIdSlave));
        assertThat("strategyID не равен", managementFee.getStrategyId(), is(strategyId));
        assertThat("Version не равен", managementFee.getVersion(), is(0));
        assertThat("NotChargedReasons не равен", managementFee.getContext().getNotChargedReasons().get(0), is("contractBlocked"));
    }


    @SneakyThrows
    @Test
    @AllureId("1593102")
    @DisplayName("1593102 CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов.SubscriptionBlocked, Slave_portfolio = null")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1593102() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "40",
            instrument.tickerFB, instrument.tradingClearingAccountFB, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //проверяем полученное событие в tracking.fee.calculate.command
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("portfolioValue не равен", feeCommand.getManagement().getPortfolioValue().getScale(), is(0));
        //проверяем запись в таблице management_fee
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 0);
        assertThat("contractID не равен", managementFee.getContractId(), is(contractIdSlave));
        assertThat("strategyID не равен", managementFee.getStrategyId(), is(strategyId));
        assertThat("Version не равен", managementFee.getVersion(), is(0));
        assertThat("NotChargedReasons не равен", managementFee.getContext().getNotChargedReasons().get(0), is("subscriptionBlocked"));
    }


    @SneakyThrows
    @Test
    @AllureId("1626445")
    @DisplayName("1626445 CalculateManagementFee.Добавление метки времени created_at. created_at = now () utc")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1626445() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "40",
            instrument.tickerFB, instrument.tradingClearingAccountFB, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаём портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "18171.04");
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //проверяем полученное событие в tracking.fee.calculate.command
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("portfolioValue не равен", feeCommand.getManagement().getPortfolioValue().getScale(), is(0));
        //проверяем запись в таблице management_fee
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("contractID не равен", managementFee.getContractId(), is(contractIdSlave));
        assertThat("strategyID не равен", managementFee.getStrategyId(), is(strategyId));
        assertThat("Version не равен", managementFee.getVersion(), is(3));
        assertThat("created_at не равен", managementFee.getCreatedAt().toInstant().getEpochSecond(), is(feeCommand.getCreatedAt().getSeconds()));
    }


    @SneakyThrows
    @Test
    @AllureId("1626447")
    @DisplayName("1626447 CalculateManagementFee.Добавление метки времени created_at, после отписки от стратегии.")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1626447() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "40",
            instrument.tickerFB, instrument.tradingClearingAccountFB, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаём портфели slave
        createSlavePOrtfolio("25000.0", "18700.02", "18171.04");
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //отписываемся от стратегии
        subscriptionApiCreator.get().deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //получаем подписку
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        //проверяем полученное событие в tracking.fee.calculate.command
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("portfolioValue не равен", feeCommand.getManagement().getPortfolioValue().getScale(), is(1));
        //проверяем запись в таблице management_fee
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3);
        assertThat("contractID не равен", managementFee.getContractId(), is(contractIdSlave));
        assertThat("strategyID не равен", managementFee.getStrategyId(), is(strategyId));
        assertThat("Version не равен", managementFee.getVersion(), is(3));
        assertThat("created_at не равен", managementFee.getCreatedAt().toInstant().getEpochSecond(), is(feeCommand.getCreatedAt().getSeconds()));
    }


    @SneakyThrows
    @Test
    @AllureId("1885415")
    @DisplayName("C1885415.CalculateManagementFee.Расчет комиссии за управление. Определения расчетных периодов." +
        "startedAt > метки времени от settlement_period_started_at, endedAt = найденный end_time.Strategy.status = frozen")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1885415() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе frozen
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.frozen, 1, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //создаем записи о расчете комиссии за управление
        createManagemetFee(subscriptionId);
        //вычитываем все события из топика tracking.fee.calculate.command
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.CalculateFeeCommand feeCommand = Tracking.CalculateFeeCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.fee.calculate.command:  {}", feeCommand);
        assertThat("subscriptionId подписки не равен", feeCommand.getSubscription().getId(), is(subscriptionId));
        assertThat("contractIdSlave не равен", feeCommand.getSubscription().getContractId(), is(contractIdSlave));
        byte[] strategyIdByteArray = feeCommand.getSubscription().getStrategy().getId().toByteArray();
        UUID guidFromByteArray = UtilsTest.getGuidFromByteArray(strategyIdByteArray);
        assertThat("subscription.strategy_id не равен", guidFromByteArray, is(strategyId));
        double rateResultScale = Math.pow(10, -1 * feeCommand.getRate().getScale());
        BigDecimal rateResult = BigDecimal.valueOf(feeCommand.getRate().getUnscaled()).multiply(BigDecimal.valueOf(rateResultScale));
        assertThat("rate result не равен", rateResult, is(new BigDecimal("0.04")));
        assertThat("rate unscaled  не равен", feeCommand.getRate().getUnscaled(), is(4L));
        assertThat("rate scale не равен", feeCommand.getRate().getScale(), is(2));
        assertThat("currency не равен", feeCommand.getCurrency().toString(), is("RUB"));
        LocalDateTime commandStartTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getStartedAt().getSeconds()), ZoneId.of("UTC"));
        LocalDateTime commandEndTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(feeCommand.getSettlementPeriod()
            .getEndedAt().getSeconds()), ZoneId.of("UTC"));
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().minusHours(3).toString()));
        managementFee = managementFeeDao.getManagementFee(contractIdSlave, this.strategyId, subscriptionId, 3);
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is("strategyFrozen"));
        double scale = Math.pow(10, -1 * feeCommand.getManagement().getPortfolioValue().getScale());
        BigDecimal value = BigDecimal.valueOf(feeCommand.getManagement().getPortfolioValue().getUnscaled()).multiply(BigDecimal.valueOf(scale));
        assertThat("value стоимости портфеля не равно", value.toString(), is("0.0"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("1885891")
    @DisplayName("C1885891.CalculateManagementFee.Расчет комиссии за управление. " +
        "Определения расчетных периодов.startedAt = subscription.start_time, endedAt = найденный end_time.Все условия по нулевой комиссии")
    @Subfeature("Успешные сценарии")
    @Description("Операция запускается по команде и инициирует расчет комиссии за управление ведомого " +
        "посредством отправки обогащенной данными команды в Тарифный модуль.")
    void C1885891() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.frozen, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "40",
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, true, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new Timestamp(endSubTime.toInstant().toEpochMilli()), true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        checkComparedToMasterFeeVersion(1, subscriptionId);
        await().atMost(TEN_SECONDS).pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 1), notNullValue());
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(3).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is("contractBlocked"));
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(1),
            is("subscriptionBlocked"));
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(2),
            is("strategyFrozen"));
    }


    // методы для работы тестов*****************************************************************

    @Step("Создаем позиции для портфеля master: ")
    List<MasterPortfolio.Position> masterPositions(Date date, String tickerOne, String tradingClearingAccountOne,
                                                   String quantityOne, String tickerTwo, String tradingClearingAccountTwo,
                                                   String quantityTwo) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerOne)
            .tradingClearingAccount(tradingClearingAccountOne)
            .quantity(new BigDecimal(quantityOne))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerTwo)
            .tradingClearingAccount(tradingClearingAccountTwo)
            .quantity(new BigDecimal(quantityTwo))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }

    @Step("Добавляем две позиции для портфеля slave c bond: ")
    List<SlavePortfolio.Position> twoSlavePositions(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("105.796"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }

    @Step("Добавляем две позиции для портфеля slave без bond: ")
    List<SlavePortfolio.Position> twoSlavePositionsNoBond(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal(quantityYNDX))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("4862.8"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }

    @Step("Добавляем две позиции для портфеля slave: ")
    List<SlavePortfolio.Position> twoSlavePositionsExchangePositionCache(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerTEST)
            .tradingClearingAccount(instrument.tradingClearingAccountTEST)
            .quantity(new BigDecimal(quantityTEST))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("4862.8"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }


    @Step("Добавляем позицию для портфеля slave: ")
    List<SlavePortfolio.Position> oneSlavePositions(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }

    @Step("Создаем записи для портфеля slave: ")
    void createSlavePOrtfolio(String baseMoneyOne, String baseMoneyTwo, String baseMoneyThree) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, baseMoneyOne,
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));

        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, baseMoneyTwo,
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusDays(2).toInstant()));

        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositions(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, baseMoneyThree,
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
    }

    @Step("Добавляем две позиции для портфеля slave без bond: ")
    void createSlavePOrtfolioNoBond(String baseMoneyOne, String baseMoneyTwo, String baseMoneyThree) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolio(contractIdSlave, strategyId, 1, 1, baseMoneyOne,
            positionList, Date.from(OffsetDateTime.now().minusDays(3).toInstant()));

        List<SlavePortfolio.Position> onePositionSlaveList = oneSlavePositions(Date.from(OffsetDateTime.now().minusDays(2).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 2, 2, baseMoneyTwo,
            onePositionSlaveList, Date.from(OffsetDateTime.now().minusDays(2).toInstant()));

        List<SlavePortfolio.Position> twoPositionSlaveList = twoSlavePositionsNoBond(Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
        steps.createSlavePortfolio(contractIdSlave, strategyId, 3, 3, baseMoneyThree,
            twoPositionSlaveList, Date.from(OffsetDateTime.now().minusDays(1).toInstant()));
    }

    @Step("Проверяем запись в management_fee: ")
    void checkManagementFeeOne(long subscriptionId) throws InterruptedException {
        LocalDateTime endPerid = LocalDate.now().minusDays(2).atStartOfDay();
        Date cutDate = Date.from(endPerid.toInstant(UTC));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal basemoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        log.info("valuePortfolio:  {}", basemoney);
        checkComparedToMasterFeeVersion(1, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 1), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(basemoney));
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(3).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("positions не равно", managementFee.getContext().getPositions().size(), is(0));

    }

    @Step("Проверяем запись в management_fee: ")
    void checkManagementFeeOneBlocked(long subscriptionId, String reasonBlocked) throws InterruptedException {
        checkComparedToMasterFeeVersion(1, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 1), notNullValue());
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(3).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is(reasonBlocked));
    }

    @Step("Проверяем запись в management_fee: ")
    void checkManagementFeeTwoBlocked(long subscriptionId, String reasonBlocked) throws InterruptedException {
        checkComparedToMasterFeeVersion(2, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 2), notNullValue());
        //Проверяем данные в management_fee
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is(reasonBlocked));
    }

    @Step("Проверяем запись в management_fee: ")
    void checkManagementFeeLastBlocked(long subscriptionId, String reasonBlocked) throws InterruptedException {
        checkComparedToMasterFeeVersion(3, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3), notNullValue());
        //Проверяем данные в management_fee
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("notChargedReasons не равно", managementFee.getContext().getNotChargedReasons().get(0),
            is(reasonBlocked));
    }

    @Step("Проверяем записи в management_fee: ")
    void checkManagementFeeTwo(long subscriptionId) throws InterruptedException {
        LocalDateTime endPerid = LocalDate.now().minusDays(1).atStartOfDay();
        Date cutDate = Date.from(endPerid.toInstant(UTC));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal basemoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(endPerid);
        String ListInst = instrument.instrumentSBER;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 1);
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal price1 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(quantitySBER).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(basemoney);
        //проверям, что если получили valuePortfolio < 0 считаем тогда valuePortfolio = 0
        if (valuePortfolio.compareTo(BigDecimal.ZERO) < 0) {
            valuePortfolio = BigDecimal.ZERO;
        }
        log.info("valuePortfolio:  {}", valuePortfolio);
        checkComparedToMasterFeeVersion(2, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 2), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(valuePortfolio));
        //Проверяем данные в management_fee
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(0).getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(0).getQuantity().toString(), is(quantitySBER));
        assertThat("price не равно", managementFee.getContext().getPositions().get(0).getPrice(), is(price1));
    }

    @Step("Проверяем записи в management_fee: ")
    void checkManagementFeeLast(long subscriptionId) throws InterruptedException {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        Date cutDate = Date.from(today.toInstant(UTC));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal baseMoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(today);
        String ListInst = instrument.instrumentSBER + "," + instrument.instrumentSU29009RMFS6;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(today);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(instrument.tickerSU29009RMFS6)
            .idKindQuery("ticker")
            .classCodeQuery(instrument.classCodeSU29009RMFS6)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal valuePos2 = BigDecimal.ZERO;
        BigDecimal price1 = BigDecimal.ZERO;
        BigDecimal price2 = BigDecimal.ZERO;

        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrument.instrumentSBER)) {
                valuePos1 = new BigDecimal(quantitySBER).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrument.instrumentSU29009RMFS6)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantitySU29009RMFS6);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantitySU29009RMFS6);
                ;
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(baseMoney);
        //проверям, что если получили valuePortfolio < 0 считаем тогда valuePortfolio = 0
        if (valuePortfolio.compareTo(BigDecimal.ZERO) < 0) {
            valuePortfolio = BigDecimal.ZERO;
        }
        log.info("valuePortfolio:  {}", valuePortfolio);
        checkComparedToMasterFeeVersion(3, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(valuePortfolio));
        //Проверяем данные в management_fee
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(0).getTicker(), is(instrument.tickerSU29009RMFS6));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountSU29009RMFS6));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(0).getQuantity().toString(), is(quantitySU29009RMFS6));
        assertThat("price не равно", managementFee.getContext().getPositions().get(0).getPrice(), is(price2));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(1).getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(1).getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(1).getQuantity().toString(), is(quantitySBER));
        assertThat("price не равно", managementFee.getContext().getPositions().get(1).getPrice(), is(price1));
    }

    @Step("Проверяем записи в management_fee: ")
    void checkManagementFeeLastNoBond(long subscriptionId, Date cutDate) throws InterruptedException {
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal baseMoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateTs = formatter.format(cutDate);
        String ListInst = instrument.instrumentSBER + "," + instrument.instrumentYNDX;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        BigDecimal valuePortfolio = steps.getPorfolioValueTwoInstruments(instrument.instrumentSBER, instrument.instrumentYNDX, dateTs,
            quantitySBER, quantityYNDX, baseMoney);
        BigDecimal price1 = steps.getPrice(pricesPos, instrument.instrumentSBER);
        BigDecimal price3 = steps.getPrice(pricesPos, instrument.instrumentYNDX);
        checkComparedToMasterFeeVersion(3, subscriptionId);
        await().atMost(TEN_SECONDS).ignoreExceptions().pollDelay(Duration.ofNanos(600)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(valuePortfolio));
        //Проверяем данные в management_fee
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().atStartOfDay().minusHours(3).toInstant(UTC).toString()));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(0).getTicker(), is(instrument.tickerYNDX));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountYNDX));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(0).getQuantity().toString(), is(quantityYNDX));
        assertThat("price не равно", managementFee.getContext().getPositions().get(0).getPrice(), is(price3));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(1).getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(1).getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(1).getQuantity().toString(), is(quantitySBER));
        assertThat("price не равно", managementFee.getContext().getPositions().get(1).getPrice(), is(price1));
    }

    @Step("Проверяем записи в management_fee: ")
    void createManagemetFee(long subscriptionId) {
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 1,
            Date.from(LocalDate.now().minusDays(3).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(2).atStartOfDay().toInstant(UTC)), context, Date.from(LocalDate.now().atStartOfDay().toInstant(UTC)));
        List<Context.Positions> positionListWithPos = new ArrayList<>();
        positionListWithPos.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("335.04"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(2).toInstant()))
            .build());
        Context contextWithPos = Context.builder()
            .portfolioValue(new BigDecimal("25400.82"))
            .positions(positionListWithPos)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 2,
            Date.from(LocalDate.now().minusDays(2).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(1).atStartOfDay().toInstant(UTC)), contextWithPos, Date.from(LocalDate.now().atStartOfDay().toInstant(UTC)));
    }

    @Step("Проверяем записи в management_fee: ")
    void createManagemetFeeWithBlocked(long subscriptionId) {
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 1,
            Date.from(LocalDate.now().minusDays(3).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(2).atStartOfDay().toInstant(UTC)), context, Date.from(LocalDate.now().atStartOfDay().toInstant(UTC)));


        List<String> contractBlocked = new ArrayList<>();
        contractBlocked.add("contractBlocked");
        Context contextWithBlocked = Context.builder()
            .notChargedReasons(contractBlocked)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 2,
            Date.from(LocalDate.now().minusDays(2).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(1).atStartOfDay().toInstant(UTC)), contextWithBlocked, Date.from(LocalDate.now().atStartOfDay().toInstant(UTC)));
    }

    void checkComparedToMasterFeeVersion(int version, long subscriptionId) throws InterruptedException {
        await().atMost(Duration.ofSeconds(12)).pollDelay(Duration.ofSeconds(5)).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, version), notNullValue());
    }
}
