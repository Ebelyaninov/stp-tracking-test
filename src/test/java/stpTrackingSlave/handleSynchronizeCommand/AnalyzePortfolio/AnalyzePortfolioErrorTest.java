package stpTrackingSlave.handleSynchronizeCommand.AnalyzePortfolio;


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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("handleSynchronizeCommand -Анализ портфеля и фиксация результата")
@Feature("TAP-7930")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleSynchronizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class
})

public class AnalyzePortfolioErrorTest {

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
    Subscription subscription;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    long subscriptionId;
    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;
    UUID investIdSlave;
    UUID investIdMaster;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdAnalyzeMasterError;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdAnalyzeSlaveError;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
    }

    String description = "description test стратегия autotest update adjust base currency";

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
    @AllureId("681110")
    @DisplayName("C681110.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Позиция не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C681110() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,null, "2.0", "FIFA",
            "TKCBM_TCAB", UUID.randomUUID(), "2.0", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionList);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
       //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(3)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(0));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }



    @SneakyThrows
    @Test
    @AllureId("875206")
    @DisplayName("C875206.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Позиция не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C875206() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos("FIFA",
            "NDS000000001", null, "2.0", date, 1,
            new BigDecimal("4626.6"), new BigDecimal("0"), new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(3)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }



    @SneakyThrows
    @Test
    @AllureId("1896522")
    @DisplayName("C1896522.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        " Trade_clearing_account позиции из настройки not-available-trading-clearing-account-list")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1896522() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL, "2.0", instrument.tickerAEE1,
            instrument.tradingClearingAccountAEE1, null, "2.0", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(2)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("Проверяем positions", slavePortfolio.getPositions().size(), is(1));
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("Проверяем positionId", position.get(0).getPositionId(), is(instrument.positionIdAAPL));
    }


    @SneakyThrows
    @Test
    @AllureId("1896525")
    @DisplayName("C1896525.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        " Trade_clearing_account позиции из настройки not-available-trading-clearing-account-list")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1896525() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, null,"2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAEE1,
            instrument.tradingClearingAccountAEE1, null, "2.0", date, 1,
            new BigDecimal("4626.6"), new BigDecimal("0"), new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(2)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("Проверяем positionId", position.get(0).getPositionId(), is(instrument.positionIdAAPL));
    }




    private Stream<Arguments> provideInstrument() {
        return Stream.of(
            Arguments.of(instrument.tickerYNDX,instrument.tradingClearingAccountYNDX, instrument.positionIdYNDX),
            Arguments.of(instrument.tickerTEUR,instrument.tradingClearingAccountTEUR, instrument.positionIdTEUR )
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideInstrument")
    @AllureId("682320")
    @DisplayName("C682320.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        " Позиции значение currency != strategy.base_currency")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C682320(String ticker,String tradingClearingAccount, UUID positionId) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2.0", ticker, tradingClearingAccount,
            positionId, "2.0", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionListSl);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
//        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        checkParam(baseMoneySlave, utc);
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        assertThat("Проверяем positionId", position.get(0).getPositionId(), is(instrument.positionIdAAPL));
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideInstrument")
    @AllureId("872438")
    @DisplayName("C872438.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Позиции значение currency != strategy.base_currency")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872438(String ticker,String tradingClearingAccount, UUID positionId) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker,
            tradingClearingAccount, positionId, "2.0", date, 1, new BigDecimal("4626.6"),
            new BigDecimal("0"), new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
//        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        checkParam(baseMoneySlave, utc);
    }


    @SneakyThrows
    @Test
    @AllureId("872622")
    @DisplayName("C872622.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Exchange available: false")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872622() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2.0",
            instrument.tickerTBIO, instrument.tradingClearingAccountTBIO, instrument.positionIdTBIO, "2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionListSl);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
//        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        checkParam(baseMoneySlave, utc);
    }


    @SneakyThrows
    @Test
    @AllureId("872642")
    @DisplayName("C872642.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Type у позиции NOT IN (available-instrument-types)")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872642() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL,"2.0",
            instrument.tickerBRJ1, instrument.tradingClearingAccountBRJ1, null,
            "2.0", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionListSl);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(2)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        checkParam(baseMoneySlave, utc);
    }

    @SneakyThrows
    @Test
    @AllureId("872663")
    @DisplayName("C872663.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Type у позиции NOT IN (available-instrument-types)")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872663() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL,"2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerBRJ1,
            instrument.tradingClearingAccountBRJ1, null,"2.0", date, 1,
            new BigDecimal("626.6"), new BigDecimal("0"),new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
//        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        checkParam(baseMoneySlave, utc);
    }


    @SneakyThrows
    @Test
    @AllureId("874591")
    @DisplayName("C874591.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Значение otc_flag у позиции = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C874591() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2.0",
            instrument.tickerNMR, instrument.tradingClearingAccountNMR, instrument.positionIdNMR,
            "2.0", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionListSl);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
//        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        checkParam(baseMoneySlave, utc);
    }


    @SneakyThrows
    @Test
    @AllureId("874637")
    @DisplayName("C874637.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Значение otc_flag у позиции = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C874637() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL,"2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerNMR,
            instrument.tradingClearingAccountNMR, instrument.positionIdNMR,"2.0", date, 1, new BigDecimal("626.6"),
            new BigDecimal("0"), new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
//        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        checkParam(baseMoneySlave, utc);
    }


    @SneakyThrows
    @Test
    @AllureId("682333")
    @DisplayName("C682333.AnalyzePortfolio.Анализ портфеля.Не найдена цена позиции price_type = 'last' в кеш exchangePositionPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C682333() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL, "2.0",
            instrument.tickerBCR, instrument.tradingClearingAccountBCR, instrument.positionIdBCR, "2.0",
            date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionListSl);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(3)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(0));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }



    @SneakyThrows
    @Test
    @AllureId("1496787")
    @DisplayName("1496787 AnalyzePortfolio. Позиция есть в master_portfolio, но не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1496787() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerTEST,
            instrument.tradingClearingAccountTEST, null, "2.0", instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "2.0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        OffsetDateTime startSubTime = OffsetDateTime.now();
        // создаём подписку на стратегию
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, positionListSl);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            contractService.getContract(contractIdSlave).getBlocked(), is(true));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(3)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave)).collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1495265")
    @DisplayName("1495265 AnalyzePortfolio. Позиция есть в slave_portfolio, но не найдена в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1495265() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, instrument.positionIdABBV,"2.0",
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            "2.0", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "2259.17", masterPos);
        OffsetDateTime startSubTime = OffsetDateTime.now();
        // создаём подписку на стратегию
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerTEST,
            instrument.tradingClearingAccountTEST, null,"2.0", date, 1, new BigDecimal("626.6"),
            new BigDecimal("0"),new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            contractService.getContract(contractIdSlave).getBlocked(), is(true));
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(3)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));
    }

    @SneakyThrows
    @Test
    @AllureId("2020449")
    @DisplayName("2020449 AnalyzePortfolio. Позиция есть в slave_portfolio, но не найдена в positionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C2020449() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerYNDX,
            instrument.tradingClearingAccountYNDX, instrument.positionIdYNDX,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        OffsetDateTime startSubTime = OffsetDateTime.now();
        // создаём подписку на стратегию
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerUSD,
            instrument.tradingClearingAccountUSD, UUID.randomUUID(),"1000", date, 1, new BigDecimal("626.6"),
            new BigDecimal("0"),new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofNanos(500)).until(() ->
            contractService.getContract(contractIdSlave).getBlocked(), is(true));
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(3)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
        //Проверяем contractSlave
        assertThat("blocked не равен", contractService.getContract(contractIdSlave).getBlocked(), is(true));
    }




    // методы для работы тестов*************************************************************************
    void checkParam(String baseMoneySlave, OffsetDateTime utc) {
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3000);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {

            }
        }
    }

}
