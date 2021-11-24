package ru.qa.tinkoff.steps.trackingAdminSteps;


import io.qameta.allure.Step;
import io.restassured.response.ResponseBodyData;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingAdminSteps {
    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final ByteArrayReceiverService kafkaReceiver;
    private final ProfileService profileService;
    private final ExchangePositionService exchangePositionService;
    private final SubscriptionService subscriptionService;
    @Autowired(required = false)
    private final MasterPortfolioDao masterPortfolioDao;
    public Client client;
    public Contract contract;
    public Strategy strategy;
    public Subscription subscription;
    Profile profile;

    ExchangePositionApi exchangePositionApi = ApiClient.api(ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    ContractApi contractApi = ru.qa.tinkoff.swagger.tracking_admin.invoker
        .ApiClient.api(ApiClient.Config.apiConfig()).contract();

    SubscriptionApi subscriptionApi = ru.qa.tinkoff.swagger.tracking.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.Config.apiConfig()).subscription();

    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    public void createClientWithContractAndStrategy(UUID investId, SocialProfile socialProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {
        //создаем запись о клиенте в tracking.client
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile, null);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(score)
            .setFeeRate(feeRateProperties);
        strategy = trackingService.saveStrategy(strategy);
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    public void createClientWithContractAndStrategyNew(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                       UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                       ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                       StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        }
        else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        client = clientService.createClient1(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false);
        strategy = trackingService.saveStrategy(strategy);
    }

    @Step("Перемещение offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }


    @Step("Данные по profile из social")
    public SocialProfile getProfile(String SIEBEL_ID) {
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname());
//            .setImage(profile.getImage().toString());
        return socialProfile;
    }


    //Метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(Client client, Contract contract, Strategy strategy, UUID investId,
                                                    SocialProfile socialProfile, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole,
                                                    ContractState contractState, UUID strategyId, String title, String description,
                                                    StrategyCurrency strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {

        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile, riskProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = contractService.saveContract(contract);

        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(score);
        strategy = trackingService.saveStrategy(strategy);
    }

    //создаем запись в tracking.exchange_position по инструменту
    public void createExchangePosition(String ticker, String tradingClearingAccount, ExchangePositionExchange exchangePositionExchange,
                                       String otcTicker, String otcClassCode) {
        Map<String, Integer> mapValue = new HashMap<String, Integer>();
        mapValue.put("default", 100);
        mapValue.put("primary", 100);
        exchangePosition = new ru.qa.tinkoff.tracking.entities.ExchangePosition()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setExchangePositionExchange(exchangePositionExchange)
            .setTrackingAllowed(false)
            .setDailyQuantityLimit(200)
            .setOrderQuantityLimits(mapValue)
            .setOtcTicker(otcTicker)
            .setOtcClassCode(otcClassCode);
        exchangePosition = exchangePositionService.saveExchangePosition(exchangePosition);
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
        contract = contractService.getContract(contractIdSlave);
    }

    //вызываем метод blockContract для slave
    public void BlockContract(String contractIdSlave) {
        contractApi.blockContract()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        contract = contractService.getContract(contractIdSlave);

    }

    public void getBlockedContractsWithParams(int limit, int cursor){
        contractApi.getBlockedContracts()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .limitQuery(limit)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
    }

    public void getBlockedContracts(){
        contractApi.getBlockedContracts()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
    }

}
