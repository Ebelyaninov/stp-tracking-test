package stpTrackingSlave.handleSynchronizeCommand.CreateSlaveOrder;

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
import org.apache.kafka.clients.producer.ProducerRecord;
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
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.steps.StpTrackingSlaveSteps;
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
import ru.tinkoff.invest.sdet.kafka.protobuf.sender.KafkaProtobufCustomSender;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


@Slf4j
@Epic("CreateSlaveOrder - Выставление заявки")
@Feature("TAP-6849")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaProtobufFactoryAutoConfiguration.class,
    ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration.class
})
public class CreateSlaveOrderTest {

    @Resource(name = "customSenderFactory")
    KafkaProtobufCustomSender<String, byte[]> kafkaSender;
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
    MasterSignalDao masterSignalDao;
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

    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.MD.invoker.ApiClient.Config.apiConfig()).prices();
    CacheApi cacheApi = ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.Config.apiConfig()).cache();
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
    String contractIdSlave = "2015430701";
    int version;
    String ticker = "AAPL";
    String tradingClearingAccount = "L01+00000SPB";
    String classCode = "SPBXM";
    BigDecimal lot = new BigDecimal("1");
    String SIEBEL_ID_MASTER = "5-AJ7L9FNI";
    String SIEBEL_ID_SLAVE = "5-15WB1PPUX";
    UUID strategyId;

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
                masterSignalDao.deleteMasterSignal(strategyId, version);
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
    @AllureId("668233")
    @DisplayName("C668233.CreateSlaveOrder.Выставление заявки.Action = 'Buy'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C668233() {
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(positionListMaster, 4, "6551.10");
        //создаем подписку на стратегию
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("2"))
            .changedAt(date)
            .build());
        String baseMoneySl = "7000.0";
        createSlavePortfolio(contractIdSlave, 2, 3, null, baseMoneySl, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
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
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceAsk = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "ask"));
        BigDecimal priceOrder = priceAsk.add(price.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "0", classCode, priceOrder, lots, ticker, tradingClearingAccount);
    }


    @SneakyThrows
    @Test
    @AllureId("705781")
    @DisplayName("C705781.CreateSlaveOrder.Выставление заявки.Action = 'Sell'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C705781() {
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("3"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(positionListMaster, 4, "6551.10");
        //создаем подписку на стратегию
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("6"))
            .changedAt(date)
            .build());
        String baseMoneySl = "7000.0";
        createSlavePortfolio(contractIdSlave, 2, 3, null, baseMoneySl, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
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
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        BigDecimal priceBid = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "bid"));
        BigDecimal priceOrder = priceBid.subtract(price.multiply(new BigDecimal("0.002")))
            .divide(new BigDecimal("0.01"), 0, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("0.01"));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
        //проверяем параметры SlaveOrder
        checkParamSlaveOrder(2, "1", "1", classCode, priceOrder, lots, ticker, tradingClearingAccount);
    }


/////////***методы для работы тестов**************************************************************************

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

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
            .setScore(1);
        strategy = trackingService.saveStrategy(strategy);
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

    // получаем данные от ценах от MarketData
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


    void createMasterPortfolio(List<MasterPortfolio.Position> positionList, int version, String money) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPosition, positionList);
    }

    //создаем портфель master в cassandra
    void createSlavePortfolio(String contractIdSlave, int version, int comparedToMasterVersion, String currency, String money,
                              List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolio(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList);
    }


    Tracking.PortfolioCommand createCommandSynchronize(String contractIdSlave) {
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
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


    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandSynTrackingSlaveCommand(String contractIdSlave) {
        //создаем команду
        Tracking.PortfolioCommand command = createCommandSynchronize(contractIdSlave);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send("tracking.slave.command", contractIdSlave, eventBytes);
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

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.event
        kafkaSender.send("tracking.event", contractIdSlave, eventBytes);
    }


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

    void checkParamSlaveOrder(int version, String attemptsCount, String action, String classCode,
                              BigDecimal price, BigDecimal lots, String ticker, String tradingClearingAccount) {
        assertThat("Version портфеля slave не равно", slaveOrder.getVersion(), is(version));
        assertThat("AttemptsCount не равно", slaveOrder.getAttemptsCount().toString(), is(attemptsCount));
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is(action));
        assertThat("ClassCode не равно", slaveOrder.getClassCode(), is(classCode));
        assertThat("IdempotencyKey пустой", slaveOrder.getIdempotencyKey(), is(notNullValue()));
        assertThat("Price не равно", slaveOrder.getPrice(), is(price));
//        assertThat("State не равно", slaveOrder.getState(), is(nullValue()));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(ticker));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccount));
    }


}
