package ru.qa.tinkoff.steps.trackingAdminSteps;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.adminCreator.ContractApiAdminCreator;
import ru.qa.tinkoff.creator.adminCreator.TimeLineApiAdminCreator;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.TimelineApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetTimelineRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetTimelineResponse;
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
import static org.junit.Assert.assertFalse;

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
    private final ContractApiAdminCreator contractApiAdminCreator;
    private final TimeLineApiAdminCreator timeLineApiAdminCreator;
    private final SlavePortfolioDao slavePortfolioDao;
    private final InvestAccountCreator<ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi> brokerAccountApiCreator;

    @Autowired(required = false)
    private final MasterPortfolioDao masterPortfolioDao;
    public Client client;
    public Contract contract;
    public Strategy strategy;
    public Subscription subscription;
    Profile profile;
    public Contract contractSlave;
    public Client clientSlave;
    public String xApiKey = "x-api-key";
    public String key ="tracking";

    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;

    @Step("Получаем данные по пользовалею из сервиса счетов: ")
    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
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
    public void createClientWithContractAndStrategy(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile, String contractId,  ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score, BigDecimal expectedRelativeYield,
                                                    String shortDescription, String ownerDescription, Boolean buyEnabled, Boolean sellEnabled,
                                                    Boolean overloaded,String result, String management ) {
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
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal(result));
        feeRateProperties.put("management", new BigDecimal(management));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy());
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
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded)
            .setExpectedRelativeYield(expectedRelativeYield)
            .setShortDescription(shortDescription)
            .setOwnerDescription(ownerDescription)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(buyEnabled)
            .setSellEnabled(sellEnabled);
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
    @Step("Cоздаем запись в tracking.exchange_position по инструменту")
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

    @Step("Cоздаем запись по портфелю мастера")
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

    //вызываем метод blockContract для slave
    @Step("Вызываем метод blockContract для slave")
    public void BlockContract(String contractIdSlave) {
        contractApiAdminCreator.get().blockContract()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(key)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        contract = contractService.getContract(contractIdSlave);

    }

    public void checkHeaders(Response response, String traceId, String serverTime){
        assertFalse(response.getHeaders().getValue(traceId).isEmpty());
        assertFalse(response.getHeaders().getValue(serverTime).isEmpty());
    }

    public void checkErrors(ErrorResponse errorResponse, String errorCode, String errorMessage ){
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is(errorCode));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is(errorMessage));

    }

    public String getTitleStrategy() {
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest " + String.valueOf(randomNumber);
        return title;
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создаем подписку для slave")
    public void createSubcription(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                   UUID strategyId,Boolean blockedContract, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                   java.sql.Timestamp dateEnd, Boolean blockedSub) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
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

    @Step("Вызываем метод getimeline")
    public GetTimelineResponse getimeline( GetTimelineRequest request) {
        GetTimelineResponse responseExep = timeLineApiAdminCreator.get().getTimeline()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetTimelineResponse.class));
        return responseExep;
    }

    @Step("Вызываем метод getimeline и получаем ошибку")
    public ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse getimelineWithError (GetTimelineRequest request, int statusCode) {
        ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse responseExep = timeLineApiAdminCreator.get().getTimeline()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(statusCode))
            .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse.class));
        return responseExep;
    }
    @Step("Создаем запись по портфелю для slave")
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

}
