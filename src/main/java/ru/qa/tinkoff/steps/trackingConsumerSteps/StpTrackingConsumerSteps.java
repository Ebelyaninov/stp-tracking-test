package ru.qa.tinkoff.steps.trackingConsumerSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.ResponseBodyData;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.IsNull;
import org.springframework.stereotype.Service;
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
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.miof.api.ClientApi;
import ru.qa.tinkoff.swagger.miof.model.RuTinkoffTradingMiddlePositionsPositionsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.SubscriptionService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingConsumerSteps {
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


    ClientApi clientMiofApi = ru.qa.tinkoff.swagger.miof.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.miof.invoker.ApiClient.Config.apiConfig()).client();

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

    //вызываем метод middleOffice по изменению позиции клиента по ценной бумаге
    public void getClientAdjustSecurityMiof(String clientCodeSlave, String contractId, String ticker, int quantity) {
        clientMiofApi.clientAdjustSecurityGet()
            .quantityQuery(quantity)
            .tickerQuery(ticker)
            .clientCodeQuery(clientCodeSlave)
            .agreementIdQuery(contractId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
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



    public List<SlavePortfolio.Position> createListSlavePositionWithTwoPosLight(String ticker1, String tradingClearingAccount1,
                                                                                String quantityPos1, String ticker2, String tradingClearingAccount2,
                                                                                String quantityPos2, Date date)    {

        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantityPos1))
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        positionList.add(SlavePortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantityPos2))
            .changedAt(date)
            .lastChangeAction(null)
            .build());
        return positionList;
    }


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
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
//            .setRole(contractRole)
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



    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
//            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
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
    public void createSubcriptionDelete(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
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
    public void createContract(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
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


    public void deleteSubscriptionSlave(String SIEBEL_ID_SLAVE, String contractIdSlave, UUID strategyId) {
        // отписываемся от стратегии
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_SLAVE)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        contractSlave = contractService.getContract(contractIdSlave);
//        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("untracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(IsNull.nullValue()));
    }

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    public void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.event
        kafkaSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
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

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId,  ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd) throws JsonProcessingException {
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
            .setEndTime(dateEnd);
            //.setPeriod(localDateTimeRange);
//            .setBlocked(blocked);
        subscription = subscriptionService.saveSubscription(subscription);

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



   public double getBaseMoneyFromMiddle(CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions, String curBaseMoney) {
        double middleQuantityBaseMoney = 0;
        //складываем позиции по валютам у которых kind =365 в map, в отдельную map складем базовую валюту и ее значение
        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getMoneyCount(); i++) {
            //значение по базовой валюте кладем в middleQuantityBaseMoney
            if ("T365" .equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
                && (curBaseMoney.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
            ) {
                middleQuantityBaseMoney = (clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getScale()))
                    + (clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getUnscaled()
                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getScale()));
            }

        }
        return middleQuantityBaseMoney;
    }

    public String getTitleStrategy(){
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" + String.valueOf(randomNumber);
        return title;
    }







}
