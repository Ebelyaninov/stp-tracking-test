package stpTrackingSlave.handleSynchronizeCommand.AnalyzePortfolio;


import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
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
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;

@Slf4j
@Epic("handleSynchronizeCommand -Анализ портфеля и фиксация результата")
@Feature("TAP-7930")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class
})

public class AnalyzePortfolioErrorTest {

    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
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
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient.api(ru.qa.tinkoff.swagger.MD.invoker
        .ApiClient.Config.apiConfig()).prices();
    SlavePortfolio slavePortfolio;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-2383868GN";
    String SIEBEL_ID_SLAVE = "4-1O6RYOAP";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
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
                createEventInTrackingEvent(contractIdSlave);
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
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("TEST")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
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
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker("TEST")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .price(new BigDecimal("4626.6"))
            .price_ts(date)
            .changedAt(date)
            .build());
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("682320")
    @DisplayName("C682320.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        " Позиции значение currency != strategy.base_currency")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C682320() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("YNDX")
            .tradingClearingAccount("L01+00000F00")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    @SneakyThrows
    @Test
    @AllureId("872438")
    @DisplayName("C872438.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Позиции значение currency != strategy.base_currency")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872438() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker("YNDX")
            .tradingClearingAccount("L01+00000F00")
            .quantity(new BigDecimal("2.0"))
            .price(new BigDecimal("4626.6"))
            .price_ts(date)
            .changedAt(date)
            .build());
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("872622")
    @DisplayName("C872622.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Exchange у позиции NOT IN (available-exchanges)")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872622() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("XS0088543190")
            .tradingClearingAccount("L01+00002F00")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    @SneakyThrows
    @Test
    @AllureId("872630")
    @DisplayName("C872630.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Exchange у позиции NOT IN (available-exchanges)")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872630() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker("XS0088543190")
            .tradingClearingAccount("L01+00002F00")
            .quantity(new BigDecimal("2.0"))
            .price(new BigDecimal("626.6"))
            .price_ts(date)
            .changedAt(date)
            .build());
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    @SneakyThrows
    @Test
    @AllureId("872642")
    @DisplayName("C872642.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Type у позиции NOT IN (available-instrument-types)")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872642() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("BRJ1")
            .tradingClearingAccount("U800")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позиции slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    @SneakyThrows
    @Test
    @AllureId("872663")
    @DisplayName("C872663.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Type у позиции NOT IN (available-instrument-types)")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C872663() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker("BRJ1")
            .tradingClearingAccount("U800")
            .quantity(new BigDecimal("2.0"))
            .price(new BigDecimal("626.6"))
            .price_ts(date)
            .changedAt(date)
            .build());
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позици slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("874591")
    @DisplayName("C874591.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для master." +
        "Значение otc_flag у позиции = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C874591() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("RU000A0JXU14")
            .tradingClearingAccount("L01+00000F00")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позици slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("874637")
    @DisplayName("C874637.AnalyzePortfolio.Анализ портфеля.Отфильтровываем недоступные позиции для slave." +
        "Значение otc_flag у позиции = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C874637() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker("RU000A0JXU14")
            .tradingClearingAccount("L01+00000F00")
            .quantity(new BigDecimal("2.0"))
            .price(new BigDecimal("626.6"))
            .price_ts(date)
            .changedAt(date)
            .build());
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        Thread.sleep(5000);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //проверяем что после анализа в портфеле slave позиция с правильной валютой
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(1));
        assertThat("Ticker позици slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is("AAPL"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("682333")
    @DisplayName("C682333.AnalyzePortfolio.Анализ портфеля.Не найдена цена позиции price_type = 'last' в кеш exchangePositionPriceCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C682333() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("AAPL")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker("SMG")
            .tradingClearingAccount("L01+00000SPB")
            .quantity(new BigDecimal("2.0"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(3, "2259.17", positionList);
        //создаем подписку для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySl = "6576.23";
        createSlavePortfolio(contractIdSlave, 1, 1, null, baseMoneySl, positionListSl, date);
        //вычитываем все события из tracking.event
        resetOffsetToLate(TRACKING_EVENT);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_EVENT, Duration.ofSeconds(20));
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
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(1));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(0));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    // методы для работы тестов*************************************************************************
    // создаем команду в топик кафка tracking.master.command
    Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId) {
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.Event event = Tracking.Event.newBuilder()
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setContract(Tracking.Contract.newBuilder()
                .setId(contractId)
                .setState(Tracking.Contract.State.TRACKED)
                .setBlocked(false)
                .build())
            .build();
        return event;
    }

    //отправляем команду на синхронизацию
    Tracking.PortfolioCommand createCommandSynchronize(String contractIdSlave, OffsetDateTime time) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.SYNCHRONIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .build();
        return command;
    }


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_EVENT, contractIdSlave, eventBytes);
    }


    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandSynTrackingSlaveCommand(String contractIdSlave, OffsetDateTime time) {
        //создаем команду
        Tracking.PortfolioCommand command = createCommandSynchronize(contractIdSlave, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));

        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1)
            .setFeeRate(feeRateProperties);
        strategy = trackingService.saveStrategy(strategy);
    }


    //вызываем метод CreateSubscription для slave
    void createSubscriptionSlave(String siebleIdSlave, String contractIdSlave, UUID strategyId) {
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebleIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        contractSlave = contractService.getContract(contractIdSlave);
    }


    void createMasterPortfolio(int version, String money, List<MasterPortfolio.Position> positionList) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }

    //создаем портфель slave в cassandra
    void createSlavePortfolio(String contractIdSlave, int version, int comparedToMasterVersion, String currency, String money,
                              List<SlavePortfolio.Position> positionList, Date date) {
        //с базовой валютой
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList, date);
    }

    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }

    void createDataToMarketData(String ticker, String classCode, String lastPrice, String askPrice, String bidPrice) {
        //получаем данные от маркет даты по ценам: last, ask, bid  и кидаем их в тестовый топик
        String last = getPriceFromMarketData(ticker + "_" + classCode, "last", lastPrice);
        String ask = getPriceFromMarketData(ticker + "_" + classCode, "ask", askPrice);
        String bid = getPriceFromMarketData(ticker + "_" + classCode, "bid", bidPrice);

        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "last", lastPrice, last);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "ask", askPrice, ask);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "bid", bidPrice, bid);
    }

    String getPriceFromMarketData(String instrumentId, String type, String priceForTest) {

        Response res = pricesApi.mdInstrumentPrices()
            .instrumentIdPath(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0]");

        if (price == null) {
            price = priceForTest;
        }
        return price;
    }

    // отправляем событие в tracking.test.md.prices.int.stream
    public void createEventTrackingTestMdPricesInStream(String instrumentId, String type, String oldPrice, String newPrice) {
        String event = PriceUpdatedEvent.getKafkaTemplate(LocalDateTime.now(ZoneOffset.UTC), instrumentId, type, oldPrice, newPrice);
        String key = PriceUpdatedKey.getKafkaTemplate(instrumentId);
        //отправляем событие в топик kafka tracking.test.md.prices.int.stream
        stringSenderService.send(Topics.TRACKING_TEST_MD_PRICES_INT_STREAM, key, event);
    }

}
