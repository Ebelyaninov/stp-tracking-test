package ru.qa.tinkoff.steps.trackingApiSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.creator.ApiCacheApiCreator;
import ru.qa.tinkoff.creator.FiregInstrumentsCreator;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.MarketDataCreator;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.entities.StrategyTailValue;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.StrategyTailValueDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.fireg.model.InstrumentGetResponse;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.trackingApiCache.model.Entity;
import ru.qa.tinkoff.tracking.entities.*;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.invest.sdet.kafka.prototype.reciever.BoostedReceiverImpl;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.swagger.trackingApiCache.invoker.ResponseSpecBuilders.shouldBeCode;
import static ru.qa.tinkoff.swagger.trackingApiCache.invoker.ResponseSpecBuilders.validatedWith;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingApiSteps {

    private final ByteArrayReceiverService kafkaReceiver;
    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final ProfileService profileService;
    private final SubscriptionService subscriptionService;
    private final InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    private final ApiCacheApiCreator<ru.qa.tinkoff.swagger.trackingApiCache.api.CacheApi> cacheApiCacheApiCreator;
    private final MarketDataCreator<PricesApi> pricesMDApiCreator;
    private final FiregInstrumentsCreator<InstrumentsApi> firegInstrumentsApiCreator;
    private final BoostedReceiverImpl<String, byte[]> boostedReceiver;
    private final StrategyService strategyService;

    @Autowired(required = false)
    MasterPortfolioDao masterPortfolioDao;
    @Autowired(required = false)
    StrategyTailValueDao strategyTailValueDao;
    @Autowired(required = false)
    MasterSignalDao masterSignalDao;
    @Autowired(required = false)
    OldKafkaService oldKafkaService;

    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategyMaster;
    public StrategyTailValue strategyTailValue;
    private final StringToByteSenderService kafkaSender;
    public Client clientSlave;
    public Contract contractSlave;
    public Subscription subscription;
    public SubscriptionBlock subscriptionBlock;
    Profile profile;


    @Step("Вызывает сервис счетов для получения данных по клиенту: ")
    @SneakyThrows
    public GetBrokerAccountsResponse getBrokerAccounts(String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }


    @Step("Вызывает сервис счетов для получения данных по клиенту: ")
    @SneakyThrows
    public GetBrokerAccountsResponse getALLAccountsFromAccount (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score,
                                                    String result, String management, Boolean overloaded, BigDecimal expectedRelativeYield,
                                                    String shortDescription, String ownerDescription) {
        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
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
        feeRateProperties.put("result", new BigDecimal(result));
        feeRateProperties.put("management", new BigDecimal(management));
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
            .setScore(score)
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded)
            .setTestsStrategy(testsStrategiesList)
            .setExpectedRelativeYield(expectedRelativeYield)
            .setShortDescription(shortDescription)
            .setOwnerDescription(ownerDescription)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithTestsContractAndStrategy(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score,
                                                    String result, String management, Boolean overloaded, BigDecimal expectedRelativeYield,
                                                    String shortDescription, String ownerDescription) {
        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
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
        feeRateProperties.put("result", new BigDecimal(result));
        feeRateProperties.put("management", new BigDecimal(management));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy().setId("russian_shares"));
        testsStrategiesList.add(new TestsStrategy().setId("option"));
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
            .setScore(score)
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded)
            .setTestsStrategy(testsStrategiesList)
            .setExpectedRelativeYield(expectedRelativeYield)
            .setShortDescription(shortDescription)
            .setOwnerDescription(ownerDescription)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategyWithOutProfile(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractState contractState,
                                                                  UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                                  ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                                  StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, String result, String management, Boolean overloaded) {

        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal(result));
        feeRateProperties.put("management", new BigDecimal(management));
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
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public Client createClientWithProfile(String SIEBLE_ID, UUID investId) {
//        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), null);
        return clientMaster;

    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createContractAndStrategy(Client clientMaster, String contractId, ContractRole contractRole, ContractState contractState,
                                          UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                          ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                          StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {

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
            .setScore(score)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    public void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile, ClientRiskProfile riskProfile) {
        clientMaster = clientService.createClient(investId, clientStatusType, socialProfile, riskProfile);
    }

    //метод создает клиента
    public void createClient(String SIEBLE_ID, UUID investId, ClientStatusType clientStatusType, ClientRiskProfile riskProfile) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, clientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()), riskProfile);
    }

    //метод создает клиента c договором
    public void createClientWithContract(String SIEBLE_ID, UUID investId, ClientStatusType clientStatusType, ClientRiskProfile riskProfile,
                                         String contractId, ContractRole contractRole, ContractState contractState,
                                         UUID strategyId) {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, clientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()), riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
    }


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());

    }

    @Step("Переместить offset для всех партиций Kafka топика {topic.name} в конец очереди")
    public void resetOffsetToEnd(Topics topic) {
        log.info("Сброс offset для топика {}", topic.getName());

        boostedReceiver.getKafkaConsumer().subscribe(Collections.singletonList(topic.getName()));
        boostedReceiver.getKafkaConsumer().poll(Duration.ofSeconds(5));
        Map<TopicPartition, Long> endOffsets = boostedReceiver.getKafkaConsumer()
            .endOffsets(boostedReceiver.getKafkaConsumer().assignment());
        HashMap<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        endOffsets.forEach((p, o) -> {
            log.info("Для partition: {} последний offset: {}", p.partition(), o);
            offsets.put(p, new OffsetAndMetadata(o));
        });
        boostedReceiver.getKafkaConsumer().commitSync(offsets);
        log.info("Offset для всех партиций Kafka топика {} перемещены в конец очереди", topic.getName());
        boostedReceiver.getKafkaConsumer().unsubscribe();
    }

    //создаем портфель master в cassandra с позицией
    @Step("Создаем портфель в master_portfolio")
    public void createMasterPortfolio(String contractIdMaster, UUID strategyId, List<MasterPortfolio.Position> positionList,
                                      int version, String money, Date date) {
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version,
            baseMoneyPosition, date, positionList);
    }


    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    @Step("Получаем значение price по инструменту из кеш exchangePositionPriceCache")
    public String getPriceFromExchangePositionPriceCache(String ticker, String tradingClearingAccount, String type, String siebelId, String instrumentId) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<Entity> resCachePrice = cacheApiCacheApiCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
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
//        @SuppressWarnings("unchecked")
//        var values = (Map<Double, Object>) prices.get(0).getValue();
//        price = values.get("price").toString();

        if (prices.size() > 0) {
            var values = (Map<Double, Object>) prices.get(0).getValue();
            price = values.get("price").toString();
        } else {
            price = getPriceFromMarketData(instrumentId, type);
        }
        return price;
    }


    @Step("Получаем значение price по инструменту из кеш exchangePositionPriceCache")
    public String getPriceFromPriceCacheOrMD(String ticker, String tradingClearingAccount, String type, String siebelId, String instrumentId) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<Entity> resCachePrice = cacheApiCacheApiCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
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
//        @SuppressWarnings("unchecked")
        if (prices.size() > 0) {
            var values = (Map<Double, Object>) prices.get(0).getValue();
            price = values.get("price").toString();
        } else {
            price = getPriceFromMarketData(instrumentId, type);
        }
        return price;
    }

    @Step("Получаем значения: aciValue и nominal по инструменту из кеш exchangePositionCache")
    public List<String> getPriceFromExchangePositionCache(String ticker, String tradingClearingAccount, String siebelId) {
        String aciValue = "";
        String nominal = "";
        List<String> dateBond = new ArrayList<>();
        //получаем содержимое кеша exchangePositionCache
        List<Entity> resCacheExchangePosition = cacheApiCacheApiCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
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


    @Step("Получаем значения: по инструментам из листа")
    public List<Entity> getInstrumentsFromExchangePositionCache(String siebelId, List<ArrayList> listOfInstrument) {
        //получаем содержимое кеша exchangePositionCache
        List<Entity> resCacheExchangePosition = cacheApiCacheApiCreator.get().getAllEntities()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .cacheNamePath("exchangePositionCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));

        var mapWithInstrument = new HashMap<>();
        List<Entity> position  = resCacheExchangePosition.stream()
                .filter(pr -> {
                    var keys = (Map<String, String>) pr.getKey();
                    for (int i = 0; i <listOfInstrument.size() ; i++) {
                        if(keys.get("ticker").equals(listOfInstrument.get(i).get(0))
                            && keys.get("tradingClearingAccount").equals(listOfInstrument.get(i).get(1))){
                            mapWithInstrument.put(pr.getKey().toString(), pr.getValue().toString());
                            return keys.get("ticker").equals(listOfInstrument.get(i).get(0))
                                && keys.get("tradingClearingAccount").equals(listOfInstrument.get(i).get(1));
                        }
                    }
                    return false;
                }
                )
                .collect(Collectors.toList());

        return position;
    }


    @Step("Получаем значения: по инструментам из листа")
    public List<String> filterPositionFromExchangePositionCache(String ticker, String tradingClearingAccount, List<Entity> positions) {
        List<String> filteredPosition = new ArrayList<>();
        String type;
        String lot;
        String classCode;
        String currency;
        String riskProfile;
        String updatedAt;
        String briefName;
        String image;
        String minPriceIncrement;
        String nominal;
        String aciValue;
        String company;
        //String customerTesting;

        List<Entity> position  = positions.stream()
            .filter(pr -> {
                    var keys = (Map<String, String>) pr.getKey();
                            return keys.get("ticker").equals(ticker)
                                && keys.get("tradingClearingAccount").equals(tradingClearingAccount);
                }
            )
            .collect(Collectors.toList());

        @SuppressWarnings("unchecked")
        var values = (Map<String, Object>) position.get(0).getValue();
        type = values.get("type").toString();
        lot = values.get("lot").toString();
        classCode = values.get("classCode").toString();
        currency = values.get("currency").toString();
        riskProfile = values.get("riskProfile").toString();
        updatedAt = values.get("updatedAt").toString();
        briefName = values.get("briefName").toString();
        image = values.get("image").toString();
        minPriceIncrement = values.get("minPriceIncrement").toString();
        company = values.get("company").toString();

        filteredPosition.add(type);
        filteredPosition.add(lot);
        filteredPosition.add(classCode);
        filteredPosition.add(currency);
        filteredPosition.add(riskProfile);
        filteredPosition.add(updatedAt);
        filteredPosition.add(briefName);
        filteredPosition.add(image);
        filteredPosition.add(minPriceIncrement);

        if (type.equals("bond")) {
            nominal = values.get("nominal").toString();
            aciValue = values.get("aciValue").toString();
            filteredPosition.add(nominal);
            filteredPosition.add(aciValue);
        }

        filteredPosition.add(company);

        return filteredPosition;
    }


    //создаем портфель master в cassandra
    @Step("Создаем портфель в master_portfolio")
    public void createMasterPortfolioWithChangedAt(String contractIdMaster, UUID strategyId, List<MasterPortfolio.Position> positionList, int version, String money, Date date) {
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, date, positionList);
    }

    @Step("Добавляем позицию в  портфель в master_portfolio")
    public List<MasterPortfolio.Position> masterOnePositions(Date date, String ticker, String tradingClearingAccount,
                                                             String quantity) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantity))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }

    @Step("Добавляем две позиции в  портфель в master_portfolio")
    public List<MasterPortfolio.Position> masterTwoPositions(Date date, String ticker1, String tradingClearingAccount1,
                                                             String quantity1, String ticker2, String tradingClearingAccount2,
                                                             String quantity2) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }

    @Step("Добавляем три позиции в  портфель в master_portfolio")
    public List<MasterPortfolio.Position> masterThreePositions(Date date, String ticker1, String tradingClearingAccount1,
                                                               String quantity1, String ticker2, String tradingClearingAccount2,
                                                               String quantity2, String ticker3, String tradingClearingAccount3,
                                                               String quantity3) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }

    @Step("Рассчитываем price в абсолютном значениепо инструменту типа bond")
    public BigDecimal valuePosBonds(String priceTs, String nominal, BigDecimal minPriceIncrement,
                                    String aciValue) {
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price = roundPrice
            .add(new BigDecimal(aciValue));
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


    public Tracking.Portfolio.Position createPosAction(Tracking.Portfolio.Action action) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        return positionAction;
    }


    // создаем портфель ведущего с позициями в кассандре
    @Step("Создать договор и стратегию в бд автоследования для ведущего клиента {client}")
    @SneakyThrows
    public void createMasterPortfolioOnePosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
    }


    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioWithOutPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }


    @Step("Запрос price в MD")
    @SneakyThrows
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

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    @Step("Отправляет событие с Action = Update, чтобы очистить кеш contractCache")
    @SneakyThrows
    public void createEventInTrackingContractEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.event
        kafkaSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }

    // создаем команду в топик кафка tracking.master.command
    @Step(" создаем команду в топик кафка TRACKING_CONTRACT_EVENT")
    @SneakyThrows
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

    @Step("Создаем подписку на стратегию: запись в client, contract, subscription: ")
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId, ClientRiskProfile clientRiskProfile, String contractId, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean subscriptionBlocked, Boolean contractBlocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient1(investId, ClientStatusType.none, null, clientRiskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(contractBlocked);
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
            .setBlocked(subscriptionBlocked);
        //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);

    }

    @Step("Создаем подписку на стратегию: запись в client, contract, subscription: ")
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionDraftOrInActive(UUID investId, ClientRiskProfile clientRiskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                 UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
                                                 java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient1(investId, ClientStatusType.none, null, clientRiskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
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
    public void createSubcriptionNotClient(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                           UUID strategyId, SubscriptionStatus subscriptionStatus, java.sql.Timestamp dateStart,
                                           java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {

        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(investId)
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


    @Step("Создаем запись в strategy_tail_value по стратегии")
    public void createDateStrategyTailValue(UUID strategyId, Date date, String value) {
        strategyTailValue = StrategyTailValue.builder()
            .strategyId(strategyId)
            .cut(date)
            .value(new BigDecimal(value))
            .build();
        strategyTailValueDao.insertIntoStrategyTailValue(strategyTailValue);
    }

    public String getTitleStrategy() {
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest " + String.valueOf(randomNumber);
        return title;
    }


    //создание записи по сигналу в табл. master_signal
    @Step("Создаем запись в master_signal по стратегии")
    public void createMasterSignalWithDateCreate(Date createdAt, int version, UUID strategyId, String ticker, String tradingClearingAccount,
                                                 String price, String quantity, String tailOrderQuantity, int action) {
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyId)
            .version(version)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .action((byte) action)
            .state((byte) 1)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .tailOrderQuantity(new BigDecimal(tailOrderQuantity))
            .createdAt(createdAt)
            .build();
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }


    @Step("Удаляем записи из strategy + contract + client")
    public void deleteDataFromDb (String SiebelId) {

        GetBrokerAccountsResponse getAllMasterAccounts = getALLAccountsFromAccount(SiebelId);
        UUID investId = getAllMasterAccounts.getInvestId();

        for(int i = 0; i < getAllMasterAccounts.getBrokerAccounts().size(); i++) {
            try {
                subscriptionService.deleteSubscription(subscriptionService.findSubcription(getAllMasterAccounts.getBrokerAccounts().get(i).getId()).get());
            } catch (Exception e) {}
            try {
                strategyService.deleteStrategy(strategyService.findStrategyByContractId(getAllMasterAccounts.getBrokerAccounts().get(i).getId()).get());
            } catch (Exception e) {}
            try {
                contractService.deleteContract(contractService.getContract(getAllMasterAccounts.getBrokerAccounts().get(i).getId()));
            } catch (Exception e) {}
        }
        try {
            clientService.deleteClient(clientService.getClient(investId));
        } catch (Exception e) {}
    }


    @Step("Создаем событие на апдейт лимитов позиции")
    public  Tracking.ExchangePosition updatePositionLimitsCommand(String ticker, String tradindClearingAccount, int dailyLimit, int additionalLiquidity,
                                                                  int defaultLimit, int mainTrading, int primary) {
        //отправляем событие на изменение лимитов
        Tracking.ExchangePosition command = Tracking.ExchangePosition.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradindClearingAccount)
            .setTrackingAllowed(true)
            .setDailyQuantityLimit(Int32Value.of(dailyLimit))
            .addOrderQuantityLimit(Tracking.ExchangePosition.OrderQuantityLimit.newBuilder()
                .setPeriodId("additional_liquidity")
                .setLimit(additionalLiquidity)
                .build())
            .addOrderQuantityLimit(Tracking.ExchangePosition.OrderQuantityLimit.newBuilder()
                .setPeriodId("default")
                .setLimit(defaultLimit)
                .build())
            .addOrderQuantityLimit(Tracking.ExchangePosition.OrderQuantityLimit.newBuilder()
                .setPeriodId("main_trading")
                .setLimit(mainTrading)
                .build())
            .addOrderQuantityLimit(Tracking.ExchangePosition.OrderQuantityLimit.newBuilder()
                .setPeriodId("primary")
                .setLimit(primary)
                .build())
            .setDynamicLimits(false)
            .build();
        return command;
    }

    @Step("Создаем событие на апдейт лимитов позиции")
    public  Tracking.ExchangePosition updatePositionDefaultLimitCommand(String ticker, String tradindClearingAccount, int dailyLimit, int defaultLimit) {
        //отправляем событие на изменение лимитов
        Tracking.ExchangePosition command = Tracking.ExchangePosition.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradindClearingAccount)
            .setTrackingAllowed(true)
            .setDailyQuantityLimit(Int32Value.of(dailyLimit))
            .addOrderQuantityLimit(Tracking.ExchangePosition.OrderQuantityLimit.newBuilder()
                .setPeriodId("default")
                .setLimit(defaultLimit)
                .build())
            .setDynamicLimits(false)
            .build();
        return command;
    }


    @Step("Получаем instrumentUID инструмента из фирега")
    public String getInstrumentUID(String ticker, String classCode){
        Response instrumentFireg = firegInstrumentsApiCreator.get().instrumentsInstrumentIdGet()
            .instrumentIdPath(ticker)
            .idKindQuery("ticker")
            .classCodeQuery(classCode)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String instrumentUID = instrumentFireg.getBody().jsonPath().getString("uid");
        return instrumentUID;
    }


    @SneakyThrows
    Object[] createJsonForCommand(String ticker, String classCode, String status, LocalDateTime time){
        String key;
        String message;
        String instrumentUID = getInstrumentUID(ticker, classCode);
        OffsetDateTime newTime = time.atOffset(ZoneOffset.UTC).minusHours(3);

        JSONObject jsonKey = new JSONObject();
        jsonKey.put("instrument_id", ticker + "_" + classCode);

        JSONObject json = new JSONObject();
        json.put("instrument_uid", instrumentUID);
        json.put("instrument_id", ticker + "_" + classCode);
        json.put("status", status);
        json.put("ts", newTime);

        key = jsonKey.toString();
        message = json.toString();

        return new Object[]{key, message};

    }

    //  метод отправляет команду в тестовый топик test.topic.to.delete
    public void createCommandForStatusCache(String ticker, String classCode, String status, LocalDateTime time) {
        //создаем команду
        Object[] command = createJsonForCommand(ticker, classCode, status, time);
        String key = (String) command[0];
        String message = (String) command[1];
        log.info("Команда в test.topic.to.delete:  {}", message);
        //отправляем событие в топик kafka test.topic.to.delete
        oldKafkaService.send(Topics.TEST_TOPIC_TO_DELETE, key, message);
    }


}
