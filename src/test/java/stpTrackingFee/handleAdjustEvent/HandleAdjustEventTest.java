package stpTrackingFee.handleAdjustEvent;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.Context;
import ru.qa.tinkoff.investTracking.entities.SlaveAdjust;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.model.CCYEV.CcyevEvent;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.steps.SptTrackingFeeStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingFeeSteps.StpTrackingFeeSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.CCYEV;

@Slf4j
@Epic("handleAdjustEvent Обработка событий о заводе ДС на счет")
@Feature("TAP-13258")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-fee")
@Tags({@Tag("stp-tracking-fee"), @Tag("handleAdjustEvent")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    SptTrackingFeeStepsConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingSiebelConfiguration.class
})
public class HandleAdjustEventTest {
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
    SlaveAdjustDao slaveAdjustDao;
    @Autowired
    OldKafkaService oldKafkaService;
    @Autowired
    ResultFeeDao resultFeeDao;
    @Autowired
    StpSiebel stpSiebel;



    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Subscription subscription;
    SlaveAdjust slaveAdjust;
    List<SlaveAdjust> slaveAdjustList;
    UUID strategyId;

    String siebelIdMaster;
    String siebelIdSlave;
    String operCode = "MNY_CHANGED_INP";

    String description = "new test стратегия autotest";
    String operId = "2321010121";

    @BeforeAll
    void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
        siebelIdSlave = stpSiebel.siebelIdSlaveStpTrackingMaster;
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
            try {
                slaveAdjustDao.deleteSlaveAdjustByStrategyAndContract(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideAction() {
        return Stream.of(
            Arguments.of("INSERT"),
            Arguments.of("UPDATE")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1442051")
    @DisplayName("C1442051.HandleAdjustEvent.Action IN ('INSERT','UPDATE').Запись в slave_adjust не найдена.Subscription.status='active'")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1442051(String action) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        LocalDateTime dateTime = LocalDateTime.now();
        String event = CcyevEvent.getKafkaTemplate(action, dateTime,
            "+12.79", "RUB", dateTime, operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("12.79"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("rub"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(false));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1452319")
    @DisplayName("C1452319.HandleAdjustEvent.Action IN ('INSERT','UPDATE').Запись в slave_adjust не найдена.Запись в result_fee найдена." +
        "EntryDateTime из события >= result_management.settlement_period_ended_at")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1452319(String action) {
        strategyId = UUID.randomUUID();
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(90));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(37);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscription.getId(), 35, startFirst,
            endFirst, context, new BigDecimal("25000.0"), endFirst);
        LocalDateTime dateTime = LocalDateTime.now();
        String event = CcyevEvent.getKafkaTemplate(action, dateTime,
            "+12.79", "RUB", dateTime, operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("12.79"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("rub"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(false));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("1453289")
    @DisplayName("C1453289.HandleAdjustEvent.Action IN ('INSERT','UPDATE').Запись в slave_adjust не найдена.Запись в result_fee найдена." +
        "EntryDateTime из события < result_management.settlement_period_ended_at")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1453289() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(90));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(37);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscription.getId(), 35, startFirst, endFirst, context, new BigDecimal("25000.0"), endFirst);
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime entryDateTime = LocalDateTime.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).minusDays(1);
        String event = CcyevEvent.getKafkaTemplate("INSERT", dateTime,
            "+12.79", "RUB", entryDateTime, operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1452026")
    @DisplayName("C1452026.HandleAdjustEvent.Action IN ('DELETE').Запись в slave_adjust не найдена.Subscription.status='active'")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1452026() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        LocalDateTime dateTime = LocalDateTime.now();
        String event = CcyevEvent.getKafkaTemplate("DELETE", dateTime,
            "+12.79", "RUB", dateTime, operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("12.79"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("rub"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(true));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1442145")
    @DisplayName("C1442145.HandleAdjustEvent.Action IN ('INSERT','UPDATE').Запись в slave_adjust найдена." +
        "ActionDateTime из события > slave_adjust.changed_at.(Quantity И amount) И (currency И ccyCode) не совпадают." +
        "Subscription.status='active'")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1442145(String action) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", false, "13.67");
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusMinutes(7);
        String event = CcyevEvent.getKafkaTemplate(action, actionDateTime,
            "+12.79", "RUB", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        List<SlaveAdjust> slaveAdjust = slaveAdjustList.stream()
            .filter(sa -> Long.valueOf(operId).equals(sa.getOperationId()))
            .collect(Collectors.toList());
        assertThat("время создания операции не равно", slaveAdjust.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjust.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjust.get(0).getQuantity().toString(), equalTo("12.79"));
        assertThat("Валюта в заводе не равно", slaveAdjust.get(0).getCurrency(), equalTo("rub"));
        assertThat("Признак удаления не равно", slaveAdjust.get(0).getDeleted(), equalTo(false));
        assertThat("время изменения опрации не равно", slaveAdjust.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(actionDateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1455912")
    @DisplayName("C1455912.HandleAdjustEvent.Action IN ('INSERT','UPDATE').Запись в slave_adjust найдена." +
        "ActionDateTime из события > slave_adjust.changed_at.(Quantity И amount) И (currency И ccyCode) не совпадают." +
        "Subscription.status='active'")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1455912(String action) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(90));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(37);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscription.getId(), 35, startFirst, endFirst, context, new BigDecimal("25000.0"), endFirst);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", false, "13.67");
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusMinutes(7);
        String event = CcyevEvent.getKafkaTemplate(action, actionDateTime,
            "+12.79", "RUB", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        List<SlaveAdjust> slaveAdjust = slaveAdjustList.stream()
            .filter(sa -> Long.valueOf(operId).equals(sa.getOperationId()))
            .collect(Collectors.toList());
        assertThat("время создания операции не равно", slaveAdjust.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjust.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjust.get(0).getQuantity().toString(), equalTo("12.79"));
        assertThat("Валюта в заводе не равно", slaveAdjust.get(0).getCurrency(), equalTo("rub"));
        assertThat("Признак удаления не равно", slaveAdjust.get(0).getDeleted(), equalTo(false));
        assertThat("время изменения опрации не равно", slaveAdjust.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(actionDateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1449500")
    @DisplayName("C1449500.HandleAdjustEvent.Запись в slave_adjust.ActionDateTime > slave_adjust.changed_at." +
        "Action IN ('INSERT', 'UPDATE').Deleted = true")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1449500(String action) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", true, "13.67");
        LocalDateTime dateTime = LocalDateTime.now();
        String event = CcyevEvent.getKafkaTemplate(action, dateTime,
            "+12.79", "USD", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("Количество записей не равно", slaveAdjustList.size(), equalTo(1));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1447679")
    @DisplayName("C1447679.HandleAdjustEvent.Обработка событий о заводе ДС на счет.Запись в slave_adjust." +
        "ActionDateTime > slave_adjust.changed_at.Action IN ('INSERT', 'UPDATE').значения параметров quantity и amount совпадают с событием")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1447679(String action) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", false, "13.67");
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusMinutes(7);
        String event = CcyevEvent.getKafkaTemplate(action, actionDateTime,
            "+13.67", "USD", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(actionDateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("1451386")
    @DisplayName("C1451386.HandleAdjustEvent.Запись в slave_adjust.ActionDateTime > slave_adjust.changed_at.Action=DELETE." +
        "Значения параметров quantity и amount не совпадают с событием.Deleted = true")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1451386() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", true, "12.17");
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusMinutes(7);
        String event = CcyevEvent.getKafkaTemplate("DELETE", actionDateTime,
            "+13.67", "USD", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("12.17"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("usd"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(true));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("1451394")
    @DisplayName("C1451394.HandleAdjustEvent.Запись в slave_adjust.ActionDateTime > slave_adjust.changed_at.Action=DELETE." +
        "Значения параметров quantity и amount не совпадают с событием.Deleted = false")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1451394() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", false, "12.17");
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusMinutes(7);
        String event = CcyevEvent.getKafkaTemplate("DELETE", actionDateTime,
            "+13.67", "USD", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("12.17"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("usd"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(true));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(actionDateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("1453378")
    @DisplayName("C1453378.HandleAdjustEvent.Запись в slave_adjust.ActionDateTime > slave_adjust.changed_at.Action=DELETE." +
        "Значения параметров quantity и amount не совпадают с событием.Deleted = false.Запись в result_fee найдена." +
        "EntryDateTime из события >= result_management.settlement_period_ended_at")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1453378() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(90));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(37);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", false, "12.17");
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscription.getId(), 35, startFirst, endFirst, context, new BigDecimal("25000.0"), endFirst);
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusMinutes(7);
        String event = CcyevEvent.getKafkaTemplate("DELETE", actionDateTime,
            "+13.67", "USD", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("12.17"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("usd"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(true));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(actionDateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    private static Stream<Arguments> provideChangeIsOld() {
        return Stream.of(
            Arguments.of("INSERT", 5),
            Arguments.of("UPDATE", 0),
            Arguments.of("DELETE", 2)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideChangeIsOld")
    @AllureId("1451633")
    @DisplayName("C1451633.HandleAdjustEvent.Запись в slave_adjust.ActionDateTime <= slave_adjust.changed_at." +
        "Action IN ('INSERT', 'UPDATE', 'DELETE').Изменение устарело")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1451633(String action, int minuts) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS);
        createsSlaveAdjust(contractIdSlave, strategyId, createDate, Long.parseLong(operId), createDate, "usd", false, "13.67");
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime actionDateTime = LocalDateTime.now().minusHours(1).minusMinutes(minuts);
        String event = CcyevEvent.getKafkaTemplate(action, actionDateTime,
            "+13.67", "USD", createDate.toLocalDateTime(), operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        assertThat("время создания операции не равно", slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjustList.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjustList.get(0).getQuantity().toString(), equalTo("13.67"));
        assertThat("Валюта в заводе не равно", slaveAdjustList.get(0).getCurrency(), equalTo("usd"));
        assertThat("Признак удаления не равно", slaveAdjustList.get(0).getDeleted(), equalTo(false));
        assertThat("время изменения опрации не равно", slaveAdjustList.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(createDate.toLocalDateTime().toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1448692")
    @DisplayName("C1448692.HandleAdjustEvent.Action IN ('INSERT', 'UPDATE').Запись в slave_adjust не найдена.Subscription.status='draft'")
    @Subfeature("Успешные сценарии")
    @Description("Операция предназначена для обработки событий о добавлении/изменении/удалении операции завода ДС на счет.")
    void C1448692(String action) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30));
        OffsetDateTime utc = OffsetDateTime.now().minusDays(5);
        Date date = Date.from(utc.toInstant());
        //создаем черновик подписки на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcriptionDraftOrInActive(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.draft, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        OffsetDateTime createDate = OffsetDateTime.now().minusHours(1);
        LocalDateTime dateTime = LocalDateTime.now();
        String event = CcyevEvent.getKafkaTemplate(action, dateTime,
            "+12.79", "USD", dateTime, operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(5)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        List<SlaveAdjust> slaveAdjust = slaveAdjustList.stream()
            .filter(sa -> Long.valueOf(operId).equals(sa.getOperationId()))
            .collect(Collectors.toList());
        assertThat("время создания операции не равно", slaveAdjust.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
        assertThat("идентификатор операции не равно", slaveAdjust.get(0).getOperationId().toString(), equalTo(operId));
        assertThat("Кол-во денежных единиц в заводе не равно", slaveAdjust.get(0).getQuantity().toString(), equalTo("12.79"));
        assertThat("Валюта в заводе не равно", slaveAdjust.get(0).getCurrency(), equalTo("usd"));
        assertThat("Признак удаления не равно", slaveAdjust.get(0).getDeleted(), equalTo(false));
        assertThat("время изменения опрации не равно", slaveAdjust.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            equalTo(dateTime.toInstant(ZoneOffset.ofHours(3)).truncatedTo(ChronoUnit.SECONDS)));
    }


    void createsSlaveAdjust(String contractId, UUID strategyId, OffsetDateTime createDate, long operationId,
                            OffsetDateTime changedAt, String currency, Boolean deleted, String quantity) {
        slaveAdjust = SlaveAdjust.builder()
            .contractId(contractId)
            .strategyId(strategyId)
            .createdAt(Date.from(createDate.toInstant()))
            .operationId(operationId)
            .quantity(new BigDecimal(quantity))
            .currency(currency)
            .deleted(deleted)
            .changedAt(Date.from(changedAt.toInstant()))
            .build();
        slaveAdjustDao.insertIntoSlaveAdjust(slaveAdjust);
    }


    public void createResultFee(String contractIdSlave, UUID strategyId, long subscriptionId,
                                int version, Date settlementPeriodStartedAt, Date settlementPeriodEndedAt,
                                Context context, BigDecimal highWaterMark, Date createAt) {
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, version,
            settlementPeriodStartedAt, settlementPeriodEndedAt, context, highWaterMark, createAt);
    }

}
