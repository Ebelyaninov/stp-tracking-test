package ru.qa.tinkoff.steps.trackingSlaveSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.miof.api.ClientApi;
import ru.qa.tinkoff.swagger.miof.model.RuTinkoffTradingMiddlePositionsPositionsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;

import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;

import ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.SubscriptionService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;
import static ru.qa.tinkoff.swagger.trackingCache.invoker.ResponseSpecBuilders.shouldBeCode;
import static ru.qa.tinkoff.swagger.trackingCache.invoker.ResponseSpecBuilders.validatedWith;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingSlaveSteps {

    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final SubscriptionService subscriptionService;
    private final ByteArrayReceiverService kafkaReceiver;
    private final StringToByteSenderService kafkaSender;
    private final MasterPortfolioDao masterPortfolioDao;
    private final SlavePortfolioDao slavePortfolioDao;
    private final StringSenderService stringSenderService;
    private final ByteToByteSenderService kafkaByteSender;

    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.MD.invoker.ApiClient.Config.apiConfig()).prices();


//        CacheApi cacheApi    = ru.qa.tinkoff.swagger.trackingApiCache.invoker.ApiClient
//        .api(ru.qa.tinkoff.swagger.trackingApiCache.invoker.ApiClient.Config.apiConfig()).cache();

    ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi cacheApi = ru.qa.tinkoff.swagger
        .trackingSlaveCache.invoker.ApiClient.api(ru.qa.tinkoff.swagger
            .trackingSlaveCache.invoker.ApiClient.Config.apiConfig()).cache();

    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.
        api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    ClientApi clientMiofApi = ru.qa.tinkoff.swagger.miof.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.miof.invoker.ApiClient.Config.apiConfig()).client();

    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategy;
    public Subscription subscription;
    public Contract contractSlave;
    public Client clientSlave;
    public Client client;
    public Contract contract;

    List<TestsStrategy> testsStrategiesList = new ArrayList<>();

    //метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategy(UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        if (contractOpt.isPresent() == true) {
            contractMaster = contractService.getContract(contractId);
            clientMaster = clientService.getClient(investId);
            contractService.deleteContract(contractMaster);
            clientService.deleteClient(clientMaster);
        }

        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
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
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));

        testsStrategiesList.add(new TestsStrategy());

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
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList);
        strategy = trackingService.saveStrategy(strategy);
    }

    public void createClientWithContract(UUID investId, ClientRiskProfile riskProfile, String contractId,ContractRole contractRole, ContractState contractState,
                                         UUID strategyId) {
        //создаем запись о клиенте в tracking.client
        client = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
    }


    //вызываем метод CreateSubscription для slave
    public void createSubscriptionSlave(String siebleIdSlave, String contractIdSlave, UUID strategyId) {
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


    public void createDataToMarketData(String ticker, String classCode, String lastPrice, String askPrice, String bidPrice) {
        //получаем данные от маркет даты по ценам: last, ask, bid  и кидаем их в тестовый топик
        String last = getPriceFromMarketData(ticker + "_" + classCode, "last", lastPrice);
        String ask = getPriceFromMarketData(ticker + "_" + classCode, "ask", askPrice);
        String bid = getPriceFromMarketData(ticker + "_" + classCode, "bid", bidPrice);
    }


    public void getPriceFromMarketDataSave(String ticker, String classCode, String typePrice, String lastPrice) {
        //получаем данные от маркет даты по ценам: last, ask, bid  и кидаем их в тестовый топик
        String priceNew = getPriceFromMarketData(ticker + "_" + classCode, typePrice, lastPrice);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, typePrice, lastPrice, priceNew);
    }


  public   String getPriceFromMarketData(String instrumentId, String type, String priceForTest) {
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


    public String getPriceFromExchangePositionPriceCacheWithSiebel(String ticker, String tradingClearingAccount, String type, String siebelId) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> resCachePrice =cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
//            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .reqSpec(r -> r.addHeader("magic-number", "4"))
            .cacheNamePath("exchangePositionPriceCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
        //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> prices = resCachePrice.stream()
            .filter(pr -> {
                    @SuppressWarnings("unchecked")
                    var keys = (Map<String, String>) pr.getKey();
                    return keys.get("ticker").equals(ticker)
                        && keys.get("tradingClearingAccount").equals(tradingClearingAccount)
                        && keys.get("priceType").equals(type);
                }
            )
            .collect(Collectors.toList());
        //достаем значение price
        @SuppressWarnings("unchecked")
        var values = (Map<Double, Object>) prices.get(0).getValue();
        price = values.get("price").toString();
        return price;
    }

    public String getPriceFromExchangePositionPriceCache(String ticker, String type) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache по певой ноде
        Response resCacheNodeZero = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("magic-number", "4"))
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


    public String getPriceFromExchangePositionPriceCacheAll(String ticker, String type, String tradingClearingAccount) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache по певой ноде
        Response resCacheNodeZero = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("magic-number", "3"))
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
            if ((ticker.equals(keyMap.get("ticker"))) & (type.equals(keyMap.get("priceType")))
                & (tradingClearingAccount.equals(keyMap.get("tradingClearingAccount")))) {
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
                if ((ticker.equals(keyMap.get("ticker"))) & (type.equals(keyMap.get("priceType")))
                    & (tradingClearingAccount.equals(keyMap.get("tradingClearingAccount")))) {
                    price = valueMap.get("price").toString();
                    break;
                }
            }

        }

        return price;
    }



    //отправляем команду на синхронизацию
    public Tracking.PortfolioCommand createCommandSynchronize(String contractIdSlave, OffsetDateTime time) {
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


    //отправляем команду на снятие технической блокировки с договора
    public Tracking.PortfolioCommand createCommandUnBlockContract(String contractIdSlave, OffsetDateTime time) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.UNBLOCK_CONTRACT)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .build();
        return command;
    }

    // создаем команду в топик кафка tracking.event
    public Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId) {
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


    // создаем команду в топик кафка tracking.event
    public Tracking.Event createEventUpdateAfterSubscriptionSlaveWithBlock(String contractId, boolean blocked) {
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
                .setBlocked(blocked)
                .build())
            .build();
        return event;
    }

    public List<MasterPortfolio.Position> createListMasterPositionWithOnePos(String ticker, String tradingClearingAccount,
                                                                             String quantityPos, Date date,
                                                                             int lastChangeDetectedVersion,
                                                                             Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }
    public List<MasterPortfolio.Position> createListMasterPositionWithTwoPos(String ticker1, String tradingClearingAccount1,
                                                                             String quantityPos1, String ticker2,
                                                                             String tradingClearingAccount2,
                                                                             String quantityPos2, Date date,
                                                                             int lastChangeDetectedVersion,
                                                                             Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }


    public List<MasterPortfolio.Position> createListMasterPositionWithThreePos(String ticker1, String tradingClearingAccount1,
                                                                             String quantityPos1, String ticker2,
                                                                             String tradingClearingAccount2,
                                                                             String quantityPos2, String ticker3,
                                                                               String tradingClearingAccount3,
                                                                               String quantityPos3, Date date,
                                                                             int lastChangeDetectedVersion,
                                                                             Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantityPos3))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }



    public void createMasterPortfolio(String contractIdMaster, UUID strategyId, int version,
                                      String money, List<MasterPortfolio.Position> positionList) {
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



    public List<SlavePortfolio.Position> createListSlavePositionWithOnePos(String ticker, String tradingClearingAccount,
                                                                           String quantityPos, Date date, Integer synchronizedToMasterVersion,
                                                                           BigDecimal price, BigDecimal rate,
                                                                           BigDecimal rateDiff, BigDecimal quantityDiff)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .synchronizedToMasterVersion(synchronizedToMasterVersion)
            .price(price)
            .rate(rate)
            .rateDiff(rateDiff)
            .quantityDiff(quantityDiff)
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        return positionList;
    }



    public List<SlavePortfolio.Position> createListSlavePositionOnePosWithEnable(String ticker, String tradingClearingAccount,
                                                                           String quantityPos, Date date, Integer synchronizedToMasterVersion,
                                                                           BigDecimal price, BigDecimal rate,
                                                                           BigDecimal rateDiff, BigDecimal quantityDiff,
                                                                                 Boolean buyEnabled,Boolean sellEnabled)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .synchronizedToMasterVersion(synchronizedToMasterVersion)
            .price(price)
//            .price_ts(date)
            .rate(rate)
            .rateDiff(rateDiff)
            .quantityDiff(quantityDiff)
            .changedAt(date)
            .lastChangeAction(null)
            .buyEnabled(buyEnabled)
            .sellEnabled(sellEnabled)
            .build());
        return positionList;
    }


    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPos(String ticker1, String tradingClearingAccount1,
                                                                           String quantityPos1,
                                                                           BigDecimal price1, BigDecimal rate1,
                                                                           BigDecimal rateDiff1, BigDecimal quantityDiff1,
                                                                           String ticker2, String tradingClearingAccount2,
                                                                           String quantityPos2, BigDecimal price2, BigDecimal rate2,
                                                                           BigDecimal rateDiff2, BigDecimal quantityDiff2,
                                                                           Date date, int synchronizedToMasterVersion)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .synchronizedToMasterVersion(synchronizedToMasterVersion)
            .price(price1)
            .rate(rate1)
            .rateDiff(rateDiff1)
            .quantityDiff(quantityDiff1)
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .synchronizedToMasterVersion(synchronizedToMasterVersion)
            .price(price2)
            .rate(rate2)
            .rateDiff(rateDiff2)
            .quantityDiff(quantityDiff2)
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        return positionList;
    }

    public List<SlavePortfolio.Position> createListSlavePositionWithOnePosLight(String ticker, String tradingClearingAccount,
                                                                                String quantityPos, Date date)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        return positionList;
    }

    public List<SlavePortfolio.Position> createListSlavePositionWithOnePosLightAndWithSellAndBuy (String ticker, String tradingClearingAccount,
                                                                                String quantityPos, Date date, Boolean buyEnabled, Boolean sellEnabled)    {
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .changedAt(date)
            .lastChangeAction(null)
            .buyEnabled(buyEnabled)
            .sellEnabled(sellEnabled)
            .build());
        return positionList;
    }



    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPosLight(String ticker1, String tradingClearingAccount1,
                                                                                String quantityPos1, Boolean buyEnableFirst,
                                                                                Boolean sellEnableFirst, String ticker2,
                                                                                String tradingClearingAccount2, String quantityPos2,
                                                                                Boolean buyEnabledSecond, Boolean sellEnableSecond,
                                                                                Date date)    {

        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date)
            .lastChangeAction(null)
            .buyEnabled(buyEnableFirst)
            .sellEnabled(sellEnableFirst)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date)
            .lastChangeAction(null)
            .buyEnabled(buyEnabledSecond)
            .sellEnabled(sellEnableSecond)
            .build());
        return positionList;
    }

    public void createSlavePortfolioWithPosition(String contractIdSlave, UUID strategyId, int version, int comparedToMasterVersion,
                                                 String money,Date date, List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .lastChangeAction(null)
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


    public Tracking.PortfolioCommand createRetrySynchronizationCommand(String contractIdSlave) {
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
    public void createEventInTrackingEvent(String contractIdSlave)  {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(Topics.TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    public void createEventInSubscriptionEvent(String contractIdSlave, UUID strategyId, long subscriptionId)  {
        //создаем событие
        ByteString strategyIdByte = byteString(strategyId);

        OffsetDateTime now = OffsetDateTime.now();
        Tracking.Event event = Tracking.Event.newBuilder()
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setSubscription(Tracking.Subscription.newBuilder()

                .setStrategy(Tracking.Strategy.newBuilder()
                    .setId(strategyIdByte)
                    .build())
                .setContractId(contractIdSlave)
                .setId(subscriptionId)
                .build())
            .build();
        byte[] bytes = event.toByteArray();
        log.info("Команда в tracking.subscription.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaByteSender.send(Topics.TRACKING_SUBSCRIPTION_EVENT, strategyIdByte.toByteArray(), eventBytes);


    }




    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    public void createEventInTrackingEventWithBlock(String contractIdSlave, boolean blocked)  {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave, blocked);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(Topics.TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }

    Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId, boolean blocked) {
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
                .setBlocked(blocked)
                .build())
            .build();
        return event;
    }

    // отправляем событие в tracking.test.md.prices.int.stream
    public void createEventTrackingTestMdPricesInStream(String instrumentId, String type, String oldPrice, String newPrice) {
        String event = PriceUpdatedEvent.getKafkaTemplate(LocalDateTime.now(ZoneOffset.UTC), instrumentId, type, oldPrice, newPrice);
        String key = PriceUpdatedKey.getKafkaTemplate(instrumentId);
        stringSenderService.send(Topics.TRACKING_TEST_MD_PRICES_INT_STREAM, key, event);
    }


    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    public  void createCommandSynTrackingSlaveCommand(String contractIdSlave) {
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandSynchronize(contractIdSlave, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }



    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    public  void createCommandUnBlockContractSlaveCommand(String contractIdSlave) {
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandUnBlockContract(contractIdSlave, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    //  метод отправляет команду с operation = 'RETRY_SYNCHRONIZE'.
    public void createCommandRetrySynTrackingSlaveCommand(String contractIdSlave)  {
        //создаем команду
        Tracking.PortfolioCommand command = createRetrySynchronizationCommand(contractIdSlave);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(Topics.TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    public  void createCommandActualizeTrackingSlaveCommand(String contractIdSlave, Tracking.PortfolioCommand command) {
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }


    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    public Tracking.Portfolio.Position createPosAction(Tracking.Portfolio.Action action) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        return positionAction;
    }

    public Tracking.Portfolio.Position createPosInCommand(String ticker, String tradingClearingAccount,
                                                          int unscaled, Tracking.Portfolio.Action action) {
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(unscaled).build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .build();
        return position;
    }
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                  ClientRiskProfile riskProfile, UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd,Boolean blocked ) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);

        subscription = subscriptionService.saveSubscription(subscription);
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionWithBlocked(UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        Optional<Contract> contractOpt = contractService.findContract(contractId);
        if (contractOpt.isPresent() == true) {
            contractMaster = contractService.getContract(contractId);
            clientMaster = clientService.getClient(investId);
            contractService.deleteContract(contractMaster);
            clientService.deleteClient(clientMaster);
        }
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        if (contractState == ContractState.untracked){
            contractSlave.setStrategyId(null);
        }
        contractSlave = contractService.saveContract(contractSlave);
        //создаем запись подписке клиента
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionDraftWithBlocked(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                             java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionWithBlockedContract(UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                             java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(true);
        if (contractState == ContractState.untracked){
            contractSlave.setStrategyId(null);
        }
        contractSlave = contractService.saveContract(contractSlave);
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    public void createBlockedContract(UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                                     UUID strategyId) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(true);
        if (contractState == ContractState.untracked){
            contractSlave.setStrategyId(null);
        }
        contractSlave = contractService.saveContract(contractSlave);
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionDraftWithBlockedContract(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(true);
        contractSlave = contractService.saveContract(contractSlave);
        String periodDefault = "[" + dateStart.toLocalDateTime() + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);
    }




//    //метод создает клиента, договор и стратегию в БД автоследования
//    public void createSubcription(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
//                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
//                                  java.sql.Timestamp dateEnd) throws JsonProcessingException {
//        //создаем запись о клиенте в tracking.client
//        clientSlave = clientService.createClient(investId, ClientStatusType.none, null);
//        // создаем запись о договоре клиента в tracking.contract
//        contractSlave = new Contract()
//            .setId(contractId)
//            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
//            .setState(contractState)
//            .setStrategyId(strategyId)
//            .setBlocked(false);
//        contractSlave = contractService.saveContract(contractSlave);
//        //создаем запись подписке клиента
//        subscription = new Subscription()
//            .setSlaveContractId(contractId)
//            .setStrategyId(strategyId)
//            .setStartTime(dateStart)
//            .setStatus(subscriptionStatus)
//            .setEndTime(dateEnd);
////            .setBlocked(blocked);
//        subscription = subscriptionService.saveSubscription(subscription);
//
//    }


    public List<String> getPriceFromExchangePositionCache(String ticker, String tradingClearingAccount, String siebelId) {
        String aciValue = "";
        String nominal = "";
        String minPriceIncrement = "";
        List<String> dateBond = new ArrayList<>();
        //получаем содержимое кеша exchangePositionCache
        String price = "";

        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> resCachePrice =cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .cacheNamePath("exchangePositionCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
        //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> position = resCachePrice.stream()
            .filter(pr -> {
                    @SuppressWarnings("unchecked")
                    var keys = (Map<String, String>) pr.getKey();
                    return keys.get("ticker").equals(ticker)
                        && keys.get("tradingClearingAccount").equals(tradingClearingAccount);

                }
            )
            .collect(Collectors.toList());
        //достаем значение price
        @SuppressWarnings("unchecked")
        var values = (Map<String, Object>) position.get(0).getValue();
        aciValue = values.get("aciValue").toString();
        nominal = values.get("currentNominal").toString();
        minPriceIncrement =values.get("minPriceIncrement").toString();
        dateBond.add(aciValue);
        dateBond.add(nominal);
        dateBond.add(minPriceIncrement);
        return dateBond;
    }


    public String getDateFromOrderCacheCacheWithSiebel(String ticker, String tradingClearingAccount, String type, String siebelId) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> resCachePrice =cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .cacheNamePath("exchangePositionPriceCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
        //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> prices = resCachePrice.stream()
            .filter(pr -> {
                    @SuppressWarnings("unchecked")
                    var keys = (Map<String, String>) pr.getKey();
                    return keys.get("ticker").equals(ticker)
                        && keys.get("tradingClearingAccount").equals(tradingClearingAccount)
                        && keys.get("priceType").equals(type);
                }
            )
            .collect(Collectors.toList());
        //достаем значение price
        @SuppressWarnings("unchecked")
        var values = (Map<Double, Object>) prices.get(0).getValue();
        price = values.get("price").toString();
        return price;
    }


    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }


    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientAndContract(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, null);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
    }

    public void getClientAdjustCurrencyMiof (String clientCodeSlave, String contractId, String tickerCurrency, int quantity) {
        clientMiofApi.clientAdjustCurrencyGet()
            .typeQuery("Withdraw")
            .amountQuery(quantity)
            .currencyQuery(tickerCurrency)
            .clientCodeQuery(clientCodeSlave)
            .agreementIdQuery(contractId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
    }


    public String getTitleStrategy(){
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" + randomNumber;
        return title;
    }

}
