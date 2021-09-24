package stpTrackingFee.calculateManagementFee;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
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
import ru.qa.tinkoff.steps.trackingFeeSteps.StpTrackingFeeSteps;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.invoker.ApiClient;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SptTrackingFeeStepsConfiguration.class
})
public class CalculateManagementFeeTest {
    @Autowired
    BillingService billingService;
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

    String siebelIdMaster = "1-51Q76AT";
    String siebelIdSlave = "5-1P87U0B13";

    String ticker1 = "SBER";
    String tradingClearingAccount1 = "L01+00002F00";
    String classCode1 = "TQBR";
    String instrumet1 = ticker1 + "_" + classCode1;
    String quantity1 = "20";

    String ticker2 = "SU29009RMFS6";
    String tradingClearingAccount2 = "L01+00002F00";
    String quantity2 = "5";
    String classCode2 = "TQOB";
    String instrumet2 = ticker2 + "_" + classCode2;
    BigDecimal minPriceIncrement = new BigDecimal("0.001");


    String ticker3 = "YNDX";
    String tradingClearingAccount3 = "L01+00002F00";
    String classCode3 = "TQBR";
    String instrumet3 = ticker3 + "_" + classCode3;
    String quantity3 = "2";

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
//            try {
//                steps.createEventInTrackingEvent(contractIdSlave);
//            } catch (Exception e) {
//            }
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40", ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
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
        //отправляем команду в топик kafka tracking.master.command
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.inactive,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
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
        //отправляем команду в топик kafka tracking.master.command
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(3);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
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
        //отправляем команду в топик kafka tracking.master.command
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
//        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);

        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.inactive,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()),false);
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
        //отправляем команду в топик kafka tracking.master.command
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40",
            ticker3, tradingClearingAccount3, "4");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1).plusHours(2);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.inactive,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()),false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        //создаем записи о расчете комиссии за управление
        createManagemetFee(subscriptionId);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.master.command
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
        assertThat("settlement_period started_at не равен", commandStartTime.toString(), is(LocalDate.now().minusDays(1).atStartOfDay().toString()));
        assertThat("settlement_period ended_at не равен", commandEndTime.toString(), is(LocalDate.now().atStartOfDay().toString()));
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40", ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
//        //создаем подписку на стратегию
//        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
//        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //изменяем дату создания подписки
//        long subscriptionId = subscription.getId().longValue();
//        OffsetDateTime updateTime = OffsetDateTime.now().minusDays(3);
//        subscription.setStartTime(new Timestamp(updateTime.toInstant().toEpochMilli()));
//        subscriptionService.saveSubscription(subscription);

        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        Optional<ManagementFee> portfolioValue = managementFeeDao.findManagementFee(contractIdSlave, strategyId, subscriptionId, 1);
        assertThat("запись по расчету комиссии за управления не равно", portfolioValue.isPresent(), is(false));
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем портфель мастера
        List<MasterPortfolio.Position> positionMasterList = masterPositions(date, ticker1, tradingClearingAccount1, "40", ticker2, tradingClearingAccount2, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9107.04", positionMasterList, date);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null,false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        //создаем портфели slave
        createSlavePOrtfolioNoBond("25000.0", "18700.02", "8974.42");
        createManagemetFee(subscriptionId);
        List<Context.Positions> positionListWithPosTwo = new ArrayList<>();
        positionListWithPosTwo.add(Context.Positions.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .price(new BigDecimal("335.04"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(2).toInstant()))
            .build());
        positionListWithPosTwo.add(Context.Positions.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .price(new BigDecimal("4821.4"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(2).toInstant()))
            .build());
        Context contextWithPosTwo = Context.builder()
            .portfolioValue(new BigDecimal("25580.22"))
            .positions(positionListWithPosTwo)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 3,
            Date.from(LocalDate.now().minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)),
            Date.from(LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)), contextWithPosTwo);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_FEE_CALCULATE_COMMAND);
        //формируем и отправляем команду на расчет комисии
        OffsetDateTime createTime = OffsetDateTime.now();
        Tracking.ActivateFeeCommand command = steps.createTrackingFeeCommand(subscriptionId, createTime);
        log.info("Команда в tracking.fee.command:  {}", command);
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.fee.calculate.command
        kafkaSender.send(TRACKING_FEE_COMMAND, contractIdSlave.getBytes(), eventBytes);
        log.info("Команда в tracking.fee.command:  {}", command);
        // проверяем, что команда в tracking.fee.calculate.command не улетает
        await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_FEE_CALCULATE_COMMAND),
                is(empty())
            ).stream().findFirst();
    }


    // методы для работы тестов*****************************************************************

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

    List<SlavePortfolio.Position> twoSlavePositions(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("105.796"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }


    List<SlavePortfolio.Position> twoSlavePositionsNoBond(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .synchronizedToMasterVersion(2)
            .price(new BigDecimal("313"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.407"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .synchronizedToMasterVersion(3)
            .price(new BigDecimal("4862.8"))
            .rate(new BigDecimal("0.0"))
            .rateDiff(new BigDecimal("0.107"))
            .quantityDiff(new BigDecimal("0.0"))
            .changedAt(date)
            .build());
        return positionList;
    }

    List<SlavePortfolio.Position> oneSlavePositions(Date date) {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
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


    void checkManagementFeeOne(long subscriptionId) throws InterruptedException {
        LocalDateTime endPerid = LocalDate.now().minusDays(2).atStartOfDay();
        Date cutDate = Date.from(endPerid.toInstant(ZoneOffset.UTC));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal basemoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        log.info("valuePortfolio:  {}", basemoney);
        checkComparedToMasterFeeVersion(1, subscriptionId);
        await().atMost(TEN_SECONDS).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 1), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(basemoney));
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("positions не равно", managementFee.getContext().getPositions().size(),
            is(0));
    }

    void checkManagementFeeTwo(long subscriptionId) throws InterruptedException {
        LocalDateTime endPerid = LocalDate.now().minusDays(1).atStartOfDay();
        Date cutDate = Date.from(endPerid.toInstant(ZoneOffset.UTC));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal basemoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(endPerid);
        String ListInst = instrumet1;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 1);
        //выполняем расчеты стоимости портфеля
        BigDecimal valuePos1 = BigDecimal.ZERO;
        BigDecimal price1 = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrumet1)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(basemoney);
        log.info("valuePortfolio:  {}", valuePortfolio);
        checkComparedToMasterFeeVersion(2, subscriptionId);
        await().atMost(TEN_SECONDS).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 2), notNullValue());

        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(valuePortfolio));
        //Проверяем данные в management_fee
        assertThat("settlement_period_started_at не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("settlement_period_ended_at не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(0).getTicker(), is(ticker1));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount1));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(0).getQuantity().toString(), is(quantity1));
        assertThat("price не равно", managementFee.getContext().getPositions().get(0).getPrice(), is(price1));
    }


    void checkManagementFeeLast(long subscriptionId) throws InterruptedException {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        Date cutDate = Date.from(today.toInstant(ZoneOffset.UTC));
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal baseMoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = fmt.format(today);
        String ListInst = instrumet1 + "," + instrumet2;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        // получаем данные для расчета по облигациям
        //получаем цены по позициям от маркет даты
        DateTimeFormatter fmtFireg = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateFireg = fmtFireg.format(today);
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker2)
            .idKindQuery("ticker")
            .classCodeQuery(classCode2)
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
            if (pair.getKey().equals(instrumet1)) {
                valuePos1 = new BigDecimal(quantity1).multiply((BigDecimal) pair.getValue());
                price1 = (BigDecimal) pair.getValue();
            }
            if (pair.getKey().equals(instrumet2)) {
                String priceTs = pair.getValue().toString();
                valuePos2 = steps.valuePosBonds(priceTs, nominal, minPriceIncrement, aciValue, quantity2);
                price2 = steps.valuePrice(priceTs, nominal, minPriceIncrement, aciValue, valuePos2, quantity2);
                ;
            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(baseMoney);
        log.info("valuePortfolio:  {}", valuePortfolio);
        checkComparedToMasterFeeVersion(3, subscriptionId);
        await().atMost(TEN_SECONDS).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(valuePortfolio));
        //Проверяем данные в management_fee
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(0).getTicker(), is(ticker2));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount2));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(0).getQuantity().toString(), is(quantity2));
        assertThat("price не равно", managementFee.getContext().getPositions().get(0).getPrice(), is(price2));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(1).getTicker(), is(ticker1));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount1));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(1).getQuantity().toString(), is(quantity1));
        assertThat("price не равно", managementFee.getContext().getPositions().get(1).getPrice(), is(price1));
    }

    void checkManagementFeeLastNoBond(long subscriptionId, Date cutDate) throws InterruptedException {
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioBefore(contractIdSlave, strategyId, cutDate);
        BigDecimal baseMoney = slavePortfolio.getBaseMoneyPosition().getQuantity();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = formatter.format(cutDate);
        String ListInst = instrumet1 + "," + instrumet3;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = steps.getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
        BigDecimal valuePortfolio = steps.getPorfolioValueTwoInstruments(instrumet1, instrumet3, dateTs,
            quantity1, quantity3, baseMoney);
        BigDecimal price1 = steps.getPrice(pricesPos, instrumet1);
        BigDecimal price3 = steps.getPrice(pricesPos, instrumet3);
        checkComparedToMasterFeeVersion(3, subscriptionId);
        await().atMost(TEN_SECONDS).until(() ->
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, 3), notNullValue());
        assertThat("value стоимости портфеля не равно", managementFee.getContext().getPortfolioValue(), is(valuePortfolio));
        //Проверяем данные в management_fee
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodStartedAt().toInstant().toString(),
            is(LocalDate.now().minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("value стоимости портфеля не равно", managementFee.getSettlementPeriodEndedAt().toInstant().toString(),
            is(LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(0).getTicker(), is(ticker3));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount3));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(0).getQuantity().toString(), is(quantity3));
        assertThat("price не равно", managementFee.getContext().getPositions().get(0).getPrice(), is(price3));
        assertThat("ticker не равно", managementFee.getContext().getPositions().get(1).getTicker(), is(ticker1));
        assertThat("tradingClearingAccount не равно", managementFee.getContext().getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount1));
        assertThat("quantity не равно", managementFee.getContext().getPositions().get(1).getQuantity().toString(), is(quantity1));
        assertThat("price не равно", managementFee.getContext().getPositions().get(1).getPrice(), is(price1));
    }

    void createManagemetFee(long subscriptionId) {
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 1,
            Date.from(LocalDate.now().minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC)),
            Date.from(LocalDate.now().minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC)), context);
        List<Context.Positions> positionListWithPos = new ArrayList<>();
        positionListWithPos.add(Context.Positions.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("335.04"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(2).toInstant()))
            .build());
        Context contextWithPos = Context.builder()
            .portfolioValue(new BigDecimal("25400.82"))
            .positions(positionListWithPos)
            .build();
        steps.createManagementFee(contractIdSlave, strategyId, subscriptionId, 2,
            Date.from(LocalDate.now().minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC)),
            Date.from(LocalDate.now().minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)), contextWithPos);
    }

    void checkComparedToMasterFeeVersion(int version, long subscriptionId) throws InterruptedException {
        Thread.sleep(3000);
        for (int i = 0; i < 5; i++) {
            managementFee = managementFeeDao.getManagementFee(contractIdSlave, strategyId, subscriptionId, version);
            if (managementFee.getVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }
}
