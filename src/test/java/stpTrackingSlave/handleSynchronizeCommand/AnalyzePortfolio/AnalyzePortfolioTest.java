package stpTrackingSlave.handleSynchronizeCommand.AnalyzePortfolio;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.*;


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
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class AnalyzePortfolioTest {

    @Autowired
    StringToByteSenderService kafkaSender;
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
    @Autowired
    StpTrackingSlaveSteps steps;


    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    Subscription subscription;
    Client clientSlave;
    String contractIdMaster;

    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";

    String tickerShareABBV = "ABBV";
    String tradingClearingAccountShareABBV = "TKCBM_TCAB";
    String classCodeShareABBV = "SPBXM";

    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-KHGHC74O";
    String SIEBEL_ID_SLAVE = "4-1O6RYOAP";
    long subscriptionId;

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
                createEventInTrackingEvent(contractIdSlave);
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
    @AllureId("681845")
    @DisplayName("C681845.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C681845() {
        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
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
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "3657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
//        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(ticker, "last", tradingClearingAccount));
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker,  tradingClearingAccount,"last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePositionRate = BigDecimal.ZERO;
        BigDecimal quantityDiff = (masterPositionRate.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPositionParameters(0, ticker, tradingClearingAccount, "0", null,
            price, slavePositionRate, masterPositionRate, quantityDiff);
        assertThat("ChangedAt позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getChangedAt(), is(nullValue()));
    }




    @SneakyThrows
    @Test
    @AllureId("683302")
    @DisplayName("C683302.AnalyzePortfolio.Набор позиций slave-портфеля, позиции в slave_portfolio и в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C683302() {
//        steps.createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
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
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "3.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9154.4", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
//        steps.createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);

        //создаем портфель для ведомого
        String baseMoneySl = "4893.36";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 1, new BigDecimal("221"), new BigDecimal("0"),
            new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
//        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(ticker, "last", tradingClearingAccount));
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker,  tradingClearingAccount,"last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("BaseMoney портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        checkPositionParameters(0, ticker, tradingClearingAccount, "2.0", null,
            price, slavePositionRate, rateDiff, quantityDiff);
        assertThat("ChangedAt позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getChangedAt().toInstant(), is(date.toInstant()));
    }



    @SneakyThrows
    @Test
    @AllureId("684579")
    @DisplayName("C684579.AnalyzePortfolio.Набор позиций slave-портфеля, позиции есть в slave_portfolio, но нет в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C684579() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        String tickerMaster = "ABBV";
        String classCodeMaster = "SPBXM";
        String tradingClearingAccountMaster = "TKCBM_TCAB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerMaster, tradingClearingAccountMaster,
            "3.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "4873.36", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySl = "5364.78";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 1, new BigDecimal("221"), new BigDecimal("0"),
            new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
//        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(ticker, "last", tradingClearingAccount));
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker,  tradingClearingAccount,"last", SIEBEL_ID_SLAVE ));
        BigDecimal priceMaster = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerMaster,  tradingClearingAccountMaster,"last", SIEBEL_ID_SLAVE));
//        BigDecimal priceMaster = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(tickerMaster, "last", tradingClearingAccountMaster));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты по бумаге slave
        BigDecimal masterPositionRate = BigDecimal.ZERO;
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //выполняем расчеты по бумаге master
        BigDecimal slavePositionRateMaster = BigDecimal.ZERO;
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceMaster);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateMaster = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiffMaster = masterPositionRateMaster.subtract(slavePositionRateMaster);
        BigDecimal quantityDiffMaster = (rateDiffMaster.multiply(slavePortfolioValue)).divide(priceMaster, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем расчеты и содержимое позиций
        assertThat("Версия  портфеля ведущего не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("BaseMoney портфеля ведущего не равна", slavePortfolio.getBaseMoneyPosition().getQuantity(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        // проверяем данные по бумаге, которая есть только в slave
        checkPositionParameters(0, ticker, tradingClearingAccount, "2.0", null,
            price, slavePositionRate, rateDiff, quantityDiff);
        assertThat("ChangedAt позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        // проверяем данные по бумаге, которая есть только в master
        checkPositionParameters(1, tickerMaster, tradingClearingAccountMaster, "0", null,
            priceMaster, slavePositionRateMaster, masterPositionRateMaster, quantityDiffMaster);
        assertThat("ChangedAt позиции в портфеле slave не равен", slavePortfolio.getPositions().get(1).getChangedAt(), is(nullValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("688348")
    @DisplayName("C688348.AnalyzePortfolio.Анализ портфеля.Набор позиций slave-портфеля по облигациям")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C688348() {
        String ticker = "VTBperp";
        String tradingClearingAccount = "TKCBM_TCAB";
        String classCode = "SPBBND";
        List<String>  list = steps.getPriceFromExchangePositionCache(ticker, tradingClearingAccount, SIEBEL_ID_MASTER);
        String aci = list.get(0);
        String nominal = list.get(1);
        String minPrIncrement = list.get(2);
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
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
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_SLAVE)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку для slave

        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "3657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal currentNominal = new BigDecimal(nominal);
        BigDecimal minPriceIncrement = new BigDecimal(minPrIncrement);
        BigDecimal aciValue = new BigDecimal(aci);
        //получаем значение price из кеша exchangePositionPriceCache
//        BigDecimal getprice = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheAll(ticker, "last", tradingClearingAccount));
        BigDecimal getprice = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker,  tradingClearingAccount,"last", SIEBEL_ID_SLAVE ));
        //расчитываетм price
        BigDecimal priceBefore = getprice.multiply(currentNominal)
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(currentNominal)
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price = roundPrice
            .add(aciValue);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePositionRate = BigDecimal.ZERO;
        BigDecimal quantityDiff = (masterPositionRate.multiply(slavePortfolioValue)).divide(price, 4, BigDecimal.ROUND_HALF_UP);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPositionParameters(0, ticker, tradingClearingAccount, "0", null,
            price, slavePositionRate, masterPositionRate, quantityDiff);
        assertThat("ChangedAt позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getChangedAt(), is(nullValue()));
    }










    // методы для работы тестов*************************************************************************
    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = steps.createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }

    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandSynTrackingSlaveCommand(String contractIdSlave, OffsetDateTime time) {
        //создаем команду
        Tracking.PortfolioCommand command = steps.createCommandSynchronize(contractIdSlave, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        Integer synchronizedToMasterVersion, BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal masterPositionRate, BigDecimal quantityDiff) {
        ;
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("SynchronizedToMasterVersion позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getSynchronizedToMasterVersion(), is(synchronizedToMasterVersion));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(masterPositionRate));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }

    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }

}
