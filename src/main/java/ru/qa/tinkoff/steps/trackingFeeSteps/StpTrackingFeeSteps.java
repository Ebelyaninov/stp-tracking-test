package ru.qa.tinkoff.steps.trackingFeeSteps;

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
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.investTracking.entities.Context;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.ManagementFeeDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.ResultFeeDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;

import ru.qa.tinkoff.swagger.trackingCache.api.CacheApi;
import ru.qa.tinkoff.swagger.trackingCache.model.Entity;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;
import static ru.qa.tinkoff.swagger.trackingCache.invoker.ResponseSpecBuilders.shouldBeCode;
import static ru.qa.tinkoff.swagger.trackingCache.invoker.ResponseSpecBuilders.validatedWith;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingFeeSteps {

    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final SubscriptionService subscriptionService;
    private final ByteArrayReceiverService kafkaReceiver;
    private final StringToByteSenderService kafkaSender;
    private final MasterPortfolioDao masterPortfolioDao;
    private final SlavePortfolioDao slavePortfolioDao;
    private final ManagementFeeDao managementFeeDao;
    private final ResultFeeDao resultFeeDao;
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient.api(ru.qa.tinkoff.swagger.MD.invoker
        .ApiClient.Config.apiConfig()).prices();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    CacheApi cacheApi = ru.qa.tinkoff.swagger.trackingCache.invoker.ApiClient.api(ru.qa.tinkoff.swagger.trackingCache.invoker.ApiClient.Config.apiConfig()).cache();
    InstrumentsApi instrumentsApi = ru.qa.tinkoff.swagger.fireg.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.fireg.invoker.ApiClient.Config.apiConfig()).instruments();


    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategyMaster;
    public Subscription subscription;
    public Contract contractSlave;
    public Client clientSlave;




    public GetBrokerAccountsResponse getBrokerAccounts(String siebelIdMaster) {
        GetBrokerAccountsResponse resAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }






    //Создаем в БД tracking данные по ведущему (Master-клиент): client, contract, strategy
    @Step("Создать договор и стратегию в бд автоследования для ведущего клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
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
       Map<String, BigDecimal> feeRate = new HashMap<>();
        feeRate.put("management", new BigDecimal("0.04"));
        feeRate.put("result",new BigDecimal("0.2"));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy());
        strategyMaster = new Strategy()
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
            .setFeeRate(feeRate)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    public void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.event
        kafkaSender.send(TRACKING_EVENT, contractIdSlave, eventBytes);
    }

    // создаем команду в топик кафка tracking.master.command
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

    // создаем команду в топик кафка tracking.fee.command
    public Tracking.ActivateFeeCommand createTrackingFeeCommand(long subscriptionId, OffsetDateTime createTime)    {
        Tracking.ActivateFeeCommand command = Tracking.ActivateFeeCommand.newBuilder()
            .setSubscription(Tracking.Subscription.newBuilder()
                .setId(subscriptionId)
                    .build())
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(createTime.toEpochSecond())
                .setNanos(createTime.getNano())
                .build())
            .setManagement(Tracking.ActivateFeeCommand.Management.newBuilder())
            .build();
        return command;
    }

    // создаем команду в топик кафка tracking.fee.command
    public Tracking.ActivateFeeCommand createTrackingFeeCommandResult(long subscriptionId, OffsetDateTime createTime)    {
        Tracking.ActivateFeeCommand command = Tracking.ActivateFeeCommand.newBuilder()
            .setSubscription(Tracking.Subscription.newBuilder()
                .setId(subscriptionId)
                .build())
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(createTime.toEpochSecond())
                .setNanos(createTime.getNano())
                .build())
            .setResult(Tracking.ActivateFeeCommand.Result.newBuilder())
            .build();
        return command;
    }


   public void createMasterPortfolio(String contractIdMaster, UUID strategyId,
                                int version,String money, List<MasterPortfolio.Position> positionList, Date date) {
        //создаем портфель master в cassandra
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }




    public void createSlavePortfolio(String contractIdSlave, UUID strategyId,
                                     int version, int comparedToMasterVersion, String money,
                                     List<SlavePortfolio.Position> positionList, Date date) {
        //создаем портфель master в cassandra
        //с базовой валютой
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .lastChangeAction((byte) 12)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, version,
            comparedToMasterVersion, baseMoneyPosition, positionList, date);
    }



    //базовая валюта с lastChangeAction
    public SlavePortfolio.BaseMoneyPosition createBaseMoney( String money, Date date,byte lastChangeAction) {
             SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .lastChangeAction(lastChangeAction)
            .build();
        return baseMoneyPosition;

    }



    // получаем данные от ценах от MarketData
    public Map<String, BigDecimal> getPriceFromMarketAllDataWithDate(String ListInst, String type, String date, int size) {
        Response res = pricesApi.mdInstrumentsPrices()
            .instrumentsIdsQuery(ListInst)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .tradeTsQuery(date)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        Map<String, BigDecimal> pricesPos = new HashMap<>();
        for (int i = 0; i < size; i++) {
            pricesPos.put(res.getBody().jsonPath().getString("instrument_id[" + i + "]"),
                new BigDecimal(res.getBody().jsonPath().getString("prices[" + i + "].price_value[0]")));
        }
        return pricesPos;
    }

    public   String getPriceFromMarketData(String instrumentId, String type,  String date) {
        Response res = pricesApi.mdInstrumentsPrices()
            .instrumentsIdsQuery(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .tradeTsQuery(date)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0]");
        price = price.substring(1);
        price = price.substring(0, price.length() - 1);
        return price;
    }



    public List<String> getPriceFromExchangePositionCache(String ticker, String tradingClearingAccount, String siebelId) {
        String aciValue = "";
        String nominal = "";
        List<String> dateBond = new ArrayList<>();
        //получаем содержимое кеша exchangePositionCache
        List<Entity> resCacheExchangePosition = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .cacheNamePath("exchangePositionCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
        //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> position = resCacheExchangePosition.stream()
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
        nominal = values.get("nominal").toString();
        dateBond.add(aciValue);
        dateBond.add(nominal);
        return dateBond;
    }





    public BigDecimal valuePosBonds(String priceTs, String nominal,BigDecimal minPriceIncrement,
                                    String aciValue, String quantity ) {
        BigDecimal valuePos = BigDecimal.ZERO;
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price =roundPrice
            .add(new BigDecimal(aciValue));
        valuePos = new BigDecimal(quantity).multiply(price);
        return valuePos;
    }

    public BigDecimal valuePrice(String priceTs, String nominal,BigDecimal minPriceIncrement,
                                    String aciValue, BigDecimal valuePos, String quantity ) {
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price =roundPrice
            .add(new BigDecimal(aciValue));
        return price;
    }


    public void createManagementFee(String contractIdSlave, UUID strategyId, long subscriptionId,
                                    int version, Date settlementPeriodStartedAt, Date settlementPeriodEndedAt,
                                    Context context, Date createdAt) {
        managementFeeDao.insertIntoManagementFee(contractIdSlave, strategyId, subscriptionId, version,
            settlementPeriodStartedAt,settlementPeriodEndedAt, context, createdAt);
    }



    public void createResultFee(String contractIdSlave, UUID strategyId, long subscriptionId,
                                    int version, Date settlementPeriodStartedAt, Date settlementPeriodEndedAt,
                                    Context context, BigDecimal highWaterMark, Date createAt) {
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, version,
            settlementPeriodStartedAt,settlementPeriodEndedAt, context, highWaterMark, createAt);
    }


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }



    public BigDecimal getPorfolioValueTwoInstruments(String instrumet1, String instrumet2,String dateTs,
                                                 String quantity1,String quantity2, BigDecimal baseMoney) {
        String ListInst = instrumet1  + "," + instrumet2;
        //вызываем метод MD и сохраняем prices в Mapinst
        Map<String, BigDecimal> pricesPos = getPriceFromMarketAllDataWithDate(ListInst, "last", dateTs, 2);
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

            }
            if (pair.getKey().equals(instrumet2)) {
                valuePos2 = new BigDecimal(quantity2).multiply((BigDecimal) pair.getValue());

            }
        }
        BigDecimal valuePortfolio = valuePos1.add(valuePos2).add(baseMoney);
        //проверям, что если получили valuePortfolio < 0 считаем тогда valuePortfolio = 0
        if (valuePortfolio.compareTo(BigDecimal.ZERO) < 0) {
            valuePortfolio = BigDecimal.ZERO;
        }
        log.info("valuePortfolio:  {}", valuePortfolio);
        return valuePortfolio;
    }

    public BigDecimal getPrice ( Map<String, BigDecimal> pricesPos, String instrumet) {
        BigDecimal price = BigDecimal.ZERO;
        Iterator it = pricesPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            if (pair.getKey().equals(instrumet)) {
                price = (BigDecimal) pair.getValue();
            }
        }
        return price;
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
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
    public void createSubcriptionDraftOrInActive(UUID investId, ClientRiskProfile clientRiskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                 UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                                 java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient1(investId, ClientStatusType.none, null, clientRiskProfile);
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

    public String getTitleStrategy() {
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest " + String.valueOf(randomNumber);
        return title;
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription1(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId,Boolean blockedContract, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blockedSub) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(blockedContract);
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
            .setBlocked(blockedSub);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);

    }


    public List<String> getDateBondFromInstrument (String ticker, String classCode, String dateFireg) {
        List<String> dateBond = new ArrayList<>();
        Response resp = instrumentsApi.instrumentsInstrumentIdAccruedInterestsGet()
            .instrumentIdPath(ticker)
            .idKindQuery("ticker")
            .classCodeQuery(classCode)
            .startDateQuery(dateFireg)
            .endDateQuery(dateFireg)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String aciValue = resp.getBody().jsonPath().getString("[0].value");
        String nominal = resp.getBody().jsonPath().getString("[0].nominal");
        dateBond.add(aciValue);
        dateBond.add(nominal);
        return dateBond;
    }













}
