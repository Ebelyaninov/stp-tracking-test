package ru.qa.tinkoff.steps.trackingSlaveSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.creator.ApiCacheSlaveCreator;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.MarketDataCreator;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;
import static ru.qa.tinkoff.swagger.trackingApiCache.invoker.ResponseSpecBuilders.shouldBeCode;
import static ru.qa.tinkoff.swagger.trackingApiCache.invoker.ResponseSpecBuilders.validatedWith;


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
    private final InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    private final MarketDataCreator<PricesApi> pricesMDApiCreator;
    private final ApiCacheSlaveCreator<CacheApi> cacheApiCacheSlaveCreator;
    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategy;
    public Subscription subscription;
    public Contract contractSlave;
    public Client clientSlave;
    public Client client;
    public Contract contract;

    String headerNameApiKey = "x-api-key";
    String apiKey = "tracking";

    List<TestsStrategy> testsStrategiesList = new ArrayList<>();

    //метод создает клиента, договор и стратегию в БД автоследования
    @SneakyThrows
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
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
//            .setRole(contractRole)
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
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategy = trackingService.saveStrategy(strategy);
    }

    @SneakyThrows
    @Step("Создать запись по клиенту и договору в бд автоследования для клиента {client}")
    public void createClientWithContract(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                         UUID strategyId) {
        //создаем запись о клиенте в tracking.client
        client = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
    }


    @SneakyThrows
    @Step("Вызов метода /instruments/{instrument_id}/prices в сервисе Market Data")
    public String getPriceFromMarketData(String instrumentId, String type) {
        Response res = pricesMDApiCreator.get().mdInstrumentPrices()
            .instrumentIdPath(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0]");
        return price;
    }

    @SneakyThrows
    @Step("Вызов метода содержимое Cache ExchangePositionPrice в приложении Slave")
    public String getPriceFromExchangePositionPriceCacheWithSiebel(String ticker, String tradingClearingAccount, String type, String siebelId) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> resCachePrice = cacheApiCacheSlaveCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader(headerNameApiKey, apiKey))
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


    @SneakyThrows
    @Step("Смотрим price по позиции в  Cache ExchangePositionPrice в приложении Slave если не нашли вызываем MD")
    public String getPriceFromPriceCacheOrMD(String ticker, String tradingClearingAccount, String instrumentId, String type) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> resCachePrice = cacheApiCacheSlaveCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader(headerNameApiKey, apiKey))
//            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .reqSpec(r -> r.addHeader("magic-number", "3"))
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
//        @SuppressWarnings("unchecked")
        if (prices.size() > 0) {
            var values = (Map<Double, Object>) prices.get(0).getValue();
            price = values.get("price").toString();
        } else {
            price = getPriceFromMarketData(instrumentId, type);
        }
        return price;
    }


    @SneakyThrows
    @Step("Вызов метода содержимое Cache ExchangePositionPrice в приложении Slave")
    public  List<Entity> getActualizeCommandCache(String contractId) {
        String price = "";
        //получаем содержимое кеша actualizeCommandCache
        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> actualizeCache = cacheApiCacheSlaveCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader(headerNameApiKey, apiKey))
//            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .reqSpec(r -> r.addHeader("magic-number", "4"))
            .cacheNamePath("actualizeCommandCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
        //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> contract = actualizeCache.stream()
            .filter(pr -> {
                    @SuppressWarnings("unchecked")
                    var keys = (Map<String, String>) pr.getKey();
                    return keys.get("contractId").equals(contractId);
                }
            )
            .collect(Collectors.toList());
/*        //достаем значение price
        @SuppressWarnings("unchecked")
        var values = (Map<Double, Object>) prices.get(0).getValue();
        price = values.get("price").toString();
        return price;*/
        return contract;
    }

    //отправляем команду на синхронизацию
    @Step("Формируем команду на синхронизацию портфелей SYNCHRONIZE: ")
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

    @Step("Формируем команду на снятие технической блокировки с договора UNBLOCK_CONTRACT: ")
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
    @Step("Формируем команду UPDATED топик кафка tracking.event: ")
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

    //отправляем команду на включение синхронизации
    @Step("Формируем команду на включение синхронизации ENABLE_SYNCHRONIZATION: ")
    public Tracking.PortfolioCommand createCommandEnableSynchronize(String contractIdSlave, OffsetDateTime time) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ENABLE_SYNCHRONIZATION)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .build();
        return command;
    }


    @Step("Добавляем позицию в портфель мастера: ")
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

    @Step("Добавляем две позиции в портфель мастера: ")
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

    @Step("Добавляем три позиции в портфель мастера: ")
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


    @Step("Создаем запись по портфелю мастера в таб. master_portfolio: ")
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


    @Step("Добавляем позицию в портфель slave: ")
    public List<SlavePortfolio.Position> createListSlavePositionWithOnePos(String ticker, String tradingClearingAccount,
                                                                           String quantityPos, Date date, Integer synchronizedToMasterVersion,
                                                                           BigDecimal price, BigDecimal rate,
                                                                           BigDecimal rateDiff, BigDecimal quantityDiff) {
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


    @Step("Добавляем позицию в портфель slave: ")
    public List<SlavePortfolio.Position> createListSlavePositionOnePosWithEnable(String ticker, String tradingClearingAccount,
                                                                                 String quantityPos, Date date, Integer synchronizedToMasterVersion,
                                                                                 BigDecimal price, BigDecimal rate,
                                                                                 BigDecimal rateDiff, BigDecimal quantityDiff,
                                                                                 Boolean buyEnabled, Boolean sellEnabled) {
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

    @Step("Добавляем две позиции в портфель slave: ")
    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPos(String ticker1, String tradingClearingAccount1,
                                                                           String quantityPos1,
                                                                           BigDecimal price1, BigDecimal rate1,
                                                                           BigDecimal rateDiff1, BigDecimal quantityDiff1,
                                                                           String ticker2, String tradingClearingAccount2,
                                                                           String quantityPos2, BigDecimal price2, BigDecimal rate2,
                                                                           BigDecimal rateDiff2, BigDecimal quantityDiff2,
                                                                           Date date, int synchronizedToMasterVersion) {
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

    @Step("Добавляем позицию в портфель slave: ")
    public List<SlavePortfolio.Position> createListSlavePositionWithOnePosLight(String ticker, String tradingClearingAccount,
                                                                                String quantityPos, Date date) {
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

    @Step("Добавляем позицию в портфель slave: ")
    public List<SlavePortfolio.Position> createListSlavePositionWithOnePosLightAndWithSellAndBuy(String ticker, String tradingClearingAccount,
                                                                                                 String quantityPos, Date date, Boolean buyEnabled, Boolean sellEnabled) {
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


    @Step("Добавляем две позиции в портфель slave: ")
    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPosLight(String ticker1, String tradingClearingAccount1,
                                                                                String quantityPos1, Boolean buyEnableFirst,
                                                                                Boolean sellEnableFirst, String ticker2,
                                                                                String tradingClearingAccount2, String quantityPos2,
                                                                                Boolean buyEnabledSecond, Boolean sellEnableSecond,
                                                                                Date date) {

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

    @Step("Создаем портфель для slave в табл. slave_portfolio: ")
    public void createSlavePortfolioWithPosition(String contractIdSlave, UUID strategyId, int version, int comparedToMasterVersion,
                                                 String money, Date date, List<SlavePortfolio.Position> positionList) {
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

    @Step("Добавляем портфель slave без позиций: ")
    public void createSlavePortfolioWithoutPosition(String contractIdSlave, UUID strategyId, int version, int comparedToMasterVersion,
                                                    String money, Date date) {
        //с базовой валютой
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .lastChangeAction(null)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithoutPosition(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, date);
    }


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }


    @Step("Отправляем команду на SYNCHRONIZATION:")
    public Tracking.PortfolioCommand createRetrySynchronizationCommand(String contractIdSlave) {
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

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    @Step("Отправляем команду в tracking.contract с Action = Update:")
    public void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlaveWithBlock(contractIdSlave, false);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(Topics.TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    @Step("Отправляем команду в tracking.subscription.event с Action = Update:")
    public void createEventInSubscriptionEvent(String contractIdSlave, UUID strategyId, long subscriptionId) {
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


    @Step("Отправляем команду в tracking.subscription.event с Action = Update:")
    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    public void createEventInTrackingEventWithBlock(String contractIdSlave, boolean blocked) {
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


    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    @Step("Отправляем команду по синхронизации в топик TRACKING_SLAVE_COMMAND: ")
    public void createCommandSynTrackingSlaveCommand(String contractIdSlave) {
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
    public void createCommandUnBlockContractSlaveCommand(String contractIdSlave) {
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
    public void createCommandRetrySynTrackingSlaveCommand(String contractIdSlave) {
        //создаем команду
        Tracking.PortfolioCommand command = createRetrySynchronizationCommand(contractIdSlave);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(Topics.TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    public void createCommandActualizeTrackingSlaveCommand(String contractIdSlave, Tracking.PortfolioCommand command) {
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    //  метод отправляет команду с operation = 'ENABLE_SYNCHRONIZATION'.
    public void createCommandEnableSynchronization(String contractIdSlave) {
        //создаем команду
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandEnableSynchronize(contractIdSlave, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(Topics.TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    @Step("Получаем данные клиента из сервиса счетов: ")
    public GetBrokerAccountsResponse getBrokerAccounts(String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
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
    @Step("Создаем подписку для slave: ")
    public void createSubcription(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                  ClientRiskProfile riskProfile, UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
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
    public void createSubcriptionWithBlocked(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
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
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        if (contractState == ContractState.untracked) {
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
    public void createSubcriptionWithBlockedContract(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                     UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
                                                     java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(true);
        if (contractState == ContractState.untracked) {
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
    public void createBlockedContract(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                      UUID strategyId) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(true);
        if (contractState == ContractState.untracked) {
            contractSlave.setStrategyId(null);
        }
        contractSlave = contractService.saveContract(contractSlave);
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionDraftWithBlockedContract(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                          UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
                                                          java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
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


    public List<String> getPriceFromExchangePositionCache(String ticker, String tradingClearingAccount, String siebelId) {
        String aciValue = "";
        String nominal = "";
        String minPriceIncrement = "";
        List<String> dateBond = new ArrayList<>();
        //получаем содержимое кеша exchangePositionCache
        String price = "";

        List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> resCachePrice = cacheApiCacheSlaveCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
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
        minPriceIncrement = values.get("minPriceIncrement").toString();
        dateBond.add(aciValue);
        dateBond.add(nominal);
        dateBond.add(minPriceIncrement);
        return dateBond;
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
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
    }


    public String getTitleStrategy() {
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" + randomNumber;
        return title;
    }

}
