package stpTrackingSlave.handleSynchronizeCommand.handleRetrySynchronizationCommand;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("handleSynchronizeCommand - Обработка команд на повторную синхронизацию")
@Feature("TAP-6843")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class, InvestTrackingAutoConfiguration.class
})
public class HandleRetrySynchronizationCommandTest {
    KafkaHelper kafkaHelper = new KafkaHelper();
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
    ExchangePositionApi exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    CacheApi cacheApi = ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.Config.apiConfig()).cache();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient.
        api(ru.qa.tinkoff.swagger.MD.invoker.ApiClient.Config.apiConfig()).prices();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave = "2007407634";
    UUID strategyId;
    String SIEBEL_ID_MASTER = "4-1V1UVPX8";
    String SIEBEL_ID_SLAVE = "5-VNLJW7PZ";

    String ticker = "AAPL";
    String tradingClearingAccount = "L01+00000SPB";
    String classCode = "SPBXM";


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
    @AllureId("739018")
    @DisplayName("C739018.SynchronizePositionResolver.Выбор позиции.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C739018() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        createDataToMarketData(ticker, classCode,"108.09", "107.79", "107.72");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("3"))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio( 3, "6551.1", positionListMaster);
        //создаем запись о slave в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("7"))
            .price(new BigDecimal("108.11"))
            .price_ts(date)
            .rate(new BigDecimal("0.0964"))
            .rateDiff(new BigDecimal("-0.0211"))
            .quantityDiff(new BigDecimal("-2"))
            .changedAt(date)
            .build());
        String baseMoneySl = "7000.0";
        createSlavePortfolioWithOutPosition(2, 3, null, baseMoneySl, positionListSl);
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            1, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
            (byte) 0, ticker, tradingClearingAccount);
        //отправляем команду на  повторную синхронизацию
        createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave

        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "7", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "bid"));
        BigDecimal priceOrder = priceAsk.subtract(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));

        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(2, "1", "2", lot, lots, priceOrder,  ticker,  tradingClearingAccount,
             classCode);
    }


    @SneakyThrows
    @Test
    @AllureId("739019")
    @DisplayName("C739019.HandleRetrySynchronizationCommand.Портфель синхронизируется: lots > 0.Slave_portfolio_position.quantity_diff > 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C739019() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        createDataToMarketData(ticker, classCode,"108.09", "107.79", "107.72");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//       создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio( 3, "6551.1", positionListMaster);
        //создаем запись о slave в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("3"))
            .price(new BigDecimal("108.11"))
            .price_ts(date)
            .rate(new BigDecimal("0.0443"))
            .rateDiff(new BigDecimal("0.0319"))
            .quantityDiff(new BigDecimal("2"))
            .changedAt(date)
            .build());
        String baseMoneySl = "7000.0";
        createSlavePortfolioWithOutPosition(2, 3, null, baseMoneySl, positionListSl);
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            0, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на  повторную синхронизацию
        createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave

        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "3", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsMax = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .divide(slavePortfolio.getPositions().get(0).getPrice()
                .add((slavePortfolio.getPositions().get(0).getPrice().multiply(new BigDecimal("0.002")))
                    .multiply(lot)), 0 , BigDecimal.ROUND_HALF_UP);
        BigDecimal lotsNew = BigDecimal.ZERO;
        if(lotsMax.compareTo(lots) < 0) {
            lotsNew = lotsMax;
        }
        if(lotsMax.compareTo(lots) > 0) {
            lotsNew = lots;
        }
        BigDecimal priceAsk = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(2, "0", "2", lot, lotsNew, priceOrder,  ticker,  tradingClearingAccount,
            classCode);
    }

    @SneakyThrows
    @Test
    @AllureId("739020")
    @DisplayName("C739020.HandleRetrySynchronizationCommand.Портфель синхронизируется: lots = 0.Выставлять повторную заявку не нужно")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C739020() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        createDataToMarketData(ticker, classCode,"108.09", "107.79", "107.72");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio( 3, "6551.1", positionListMaster);
        //создаем запись о slave в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("108.11"))
            .price_ts(date)
            .rate(new BigDecimal("0.0717"))
            .rateDiff(new BigDecimal("0.0045"))
            .quantityDiff(new BigDecimal("2"))
            .changedAt(date)
            .build());
        String baseMoneySl = "6551.1";
        createSlavePortfolioWithOutPosition(2, 3, null, baseMoneySl, positionListSl);
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            0, classCode, UUID.randomUUID(), new BigDecimal("108.11"), new BigDecimal("2"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на  повторную синхронизацию
        createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave

        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "6551.1");
        checkPositionParameters(0, ticker, tradingClearingAccount, "5", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение лотов
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        assertThat("Количество лотов не равно", lots.toString(), is("0"));
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(2));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("attemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is("1"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity().toString(), is("2"));
        assertThat("ticker бумаги не равен", slaveOrder.getPrice(), is(new BigDecimal("108.11")));
        assertThat("price бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Version заявки не равно", slaveOrder.getState().toString(), is("1"));
    }




    @SneakyThrows
    @Test
    @AllureId("738183")
    @DisplayName("C738183.HandleRetrySynchronizationCommand.Портфель не синхронизируется")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C738183() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        BigDecimal lot = new BigDecimal("1");
        createDataToMarketData(ticker, classCode,"108.09", "107.79", "107.72");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//       создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio( 3, "6551.1", positionListMaster);
        //создаем запись о slave в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("3"))
            .price(new BigDecimal("108.11"))
            .price_ts(date)
            .rate(new BigDecimal("0.0443"))
            .rateDiff(new BigDecimal("0.0319"))
            .quantityDiff(new BigDecimal("2"))
            .changedAt(date)
            .build());
        String baseMoneySl = "7000.0";
        createSlavePortfolioWithOutPosition(2, 3, null, baseMoneySl, positionListSl);
        //отправляем команду на  повторную синхронизацию
        createCommandRetrySynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave

        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePosQuantity = slavePortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        checkSlavePortfolioParameters(2, 3, "7000.0");
        checkPositionParameters(0, ticker, tradingClearingAccount, "3", price, slavePositionRate, rateDiff,
            quantityDiff);
        // рассчитываем значение
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(priceAsk.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        checkOrderParameters(2, "0", "1", lot, lots, priceOrder,  ticker,  tradingClearingAccount,
            classCode);
    }




//методы для тестов*************************************************************************************

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(String SIEBLE_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
            .setScore(1);;
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


    void createMasterPortfolio(int version,String money, List<MasterPortfolio.Position> positionList) {
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

    //создаем портфель master в cassandra
    void createSlavePortfolioWithOutPosition(int version, int comparedToMasterVersion, String currency, String money,
                                             List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList, date);
    }


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

    // отправляем событие в tracking.test.md.prices.int.stream
    public void createEventTrackingTestMdPricesInStream (String instrumentId, String type, String oldPrice, String newPrice) {
        String event = PriceUpdatedEvent.getKafkaTemplate(LocalDateTime.now(ZoneOffset.UTC), instrumentId, type, oldPrice, newPrice);
        String key = PriceUpdatedKey.getKafkaTemplate(instrumentId);
        //отправляем событие в топик kafka social.event
        KafkaTemplate<String, String> template = kafkaHelper.createStringToStringTemplate();
        template.setDefaultTopic("tracking.test.md.prices.int.stream");
        template.sendDefault(key, event);
        template.flush();
    }

    String  getPriceFromMarketData(String instrumentId, String type, String priceForTest) {

        Response res = pricesApi.mdInstrumentPrices()
            .instrumentIdPath(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0]");

        if(price == null){
            price = priceForTest;
        }
        return price;
    }


    void createDataToMarketData (String ticker, String classCode, String lastPrice, String askPrice, String bidPrice) {
        //получаем данные от маркет даты по ценам: last, ask, bid  и кидаем их в тестовый топик
        String last =  getPriceFromMarketData(ticker + "_" + classCode, "last", lastPrice);
        String ask =  getPriceFromMarketData(ticker + "_" + classCode, "ask", askPrice);
        String bid =  getPriceFromMarketData(ticker + "_" + classCode, "bid", bidPrice);

        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "last", lastPrice, last);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "ask", askPrice, ask);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "bid", bidPrice, bid);
    }

    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandRetrySynTrackingSlaveCommand(String contractIdSlave) throws InterruptedException {
        //создаем команду
        Tracking.PortfolioCommand command = createRetrySynchronizationCommand(contractIdSlave);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdSlave;
        //отправляем команду в топик kafka tracking.master.command
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.slave.command");
        template.sendDefault(keyMaster, eventBytes);
        template.flush();
        Thread.sleep(10000);
    }

    Tracking.PortfolioCommand createRetrySynchronizationCommand(String contractIdSlave) {
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.RETRY_SYNCHRONIZATION)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .build();
        return command;
    }

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) throws InterruptedException {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        String key = contractIdSlave;
        //отправляем событие в топик kafka tracking.event
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.event");
        template.sendDefault(key, eventBytes);
        template.flush();
        Thread.sleep(10000);
    }
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


    public String getPriceFromExchangePositionPriceCache(String ticker, String type) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache по певой ноде
        Response resCacheNodeZero = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("magic-number", "0"))
            .cacheNamePath("exchangePositionPriceCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);

        ArrayList<Map<String, Map<String, Object>>> lastPriceMapListPodsZero = resCacheNodeZero.getBody().jsonPath().get();
        for (Map<String, Map<String, Object>> e : lastPriceMapListPodsZero) {
            Map<String, Object> keyMap = e.get("key");
            Map<String, Object> valueMap = e.get("value");
            if ((ticker.equals(keyMap.get("ticker"))) & (type.equals(keyMap.get("priceType")))) {
                price = valueMap.get("price").toString();
                break;
            }
        }
        if ("".equals(price)) {
            Response resCacheNodeOne = cacheApi.getAllEntities()
                .reqSpec(r -> r.addHeader("api-key", "tracking"))
                .reqSpec(r -> r.addHeader("magic-number", "1"))
                .cacheNamePath("exchangePositionPriceCache")
                .xAppNameHeader("tracking")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response);

            ArrayList<Map<String, Map<String, Object>>> lastPriceMapList = resCacheNodeOne.getBody().jsonPath().get();
            for (Map<String, Map<String, Object>> e : lastPriceMapList) {
                Map<String, Object> keyMap = e.get("key");
                Map<String, Object> valueMap = e.get("value");
                if ((ticker.equals(keyMap.get("ticker"))) & (type.equals(keyMap.get("priceType")))) {
                    price = valueMap.get("price").toString();
                    break;
                }
            }

        }

        return price;
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


    public void checkOrderParameters(int version, String action, String attemptsCount,  BigDecimal lot, BigDecimal lots,
                                     BigDecimal priceOrder, String ticker, String tradingClearingAccount,
                                     String classCode) {
        assertThat("Version заявки не равно", slaveOrder.getVersion(), is(version));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("attemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is(attemptsCount));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("price бумаги не равен", slaveOrder.getPrice(), is(priceOrder));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("classCode бумаги не равен", slaveOrder.getClassCode(), is(classCode));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
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
