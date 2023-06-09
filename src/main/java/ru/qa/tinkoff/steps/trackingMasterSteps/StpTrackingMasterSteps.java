package ru.qa.tinkoff.steps.trackingMasterSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.creator.FiregInstrumentsCreator;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.adminCreator.ExchangePositionApiAdminCreator;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.swagger.fireg.api.InstrumentsApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.Exchange;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.allure.AllureMasterSteps;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingMasterSteps {

    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final SubscriptionService subscriptionService;
    private final ExchangePositionService exchangePositionService;
    private final ByteArrayReceiverService kafkaReceiver;
    private final StringToByteSenderService kafkaSender;
    private final InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    private final ExchangePositionApiAdminCreator exchangePositionApiAdminCreator;
    private final FiregInstrumentsCreator<InstrumentsApi> firegInstrumentsApiCreator;
    private final AllureMasterSteps allureSteps;

    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategyMaster;
    public Subscription subscription;
    public Contract contractSlave;
    public Client clientSlave;

    UtilsTest utilsTest = new UtilsTest();

    //Создаем в БД tracking данные по ведущему (Master-клиент): client, contract, strategy
    @Step("Создание стратегии мастеру strategyId: \"{strategyId}\" \n в статусе: {strategyStatus}, \n для контракта: {contractId} \n c базовой валютой: {strategyCurrency}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(UUID investId, ClientRiskProfile riskProfile ,String contractId, ContractRole contractRole, ContractState contractState,
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        testsStrategiesList.add(new TestsStrategy());
        List<TestsStrategy> tagsStrategiesList = new ArrayList<>();
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
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setTags(tagsStrategiesList);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //метод проверяет инструмент в tracking.exchange_position и создает его при необоходимости
    public void getExchangePosition(String ticker, String tradingClearingAccount, Exchange exchange,
                                    Boolean trackingAllowed, Integer dailyQuantityLimit) {
        //проверяем запись в tracking.exchange_position
        Optional<ru.qa.tinkoff.tracking.entities.ExchangePosition> exchangePositionOpt = exchangePositionService.findExchangePositionByTicker(ticker, tradingClearingAccount);
        if (exchangePositionOpt.isPresent() == false) {
            List<OrderQuantityLimit> orderQuantityLimitList
                = new ArrayList<>();
            OrderQuantityLimit orderQuantityLimit = new OrderQuantityLimit();
            orderQuantityLimit.setLimit(1000);
            orderQuantityLimit.setPeriodId("additional_liquidity");
            orderQuantityLimitList.add(orderQuantityLimit);
            //формируем тело запроса
            CreateExchangePositionRequest createExPosition = new CreateExchangePositionRequest();
            createExPosition.exchange(exchange);
            createExPosition.dailyQuantityLimit(dailyQuantityLimit);
            createExPosition.setOrderQuantityLimits(orderQuantityLimitList);
            createExPosition.setTicker(ticker);
            createExPosition.setTrackingAllowed(trackingAllowed);
            createExPosition.setTradingClearingAccount(tradingClearingAccount);
            //вызываем метод createExchangePosition
            exchangePositionApiAdminCreator.get().createExchangePosition()
                .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("android")
                .xDeviceIdHeader("test")
                .xTcsLoginHeader("tracking_admin")
                .body(createExPosition)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking_admin.model.UpdateExchangePositionResponse.class));
        }
    }

    //Создаем команду в топик кафка tracking.master.command
    public Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandWithDynamicLimitQuantity(String contractId, OffsetDateTime time, int version,
                                                                                   long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                                   Tracking.Portfolio.Action action, Tracking.Decimal price,
                                                                                   Tracking.Decimal quantityS, String ticker, String tradingClearingAccount,
                                                                                   Double dynamicLimitQuantity, Tracking.Decimal tailOrderQuantity) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(price)
            .setQuantity(quantityS)
            .setTailOrderQuantity(tailOrderQuantity)
            .setDynamicLimitQuantity(com.google.protobuf.DoubleValue.newBuilder()
            .setValue(dynamicLimitQuantity).build())
            .build();

        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .setSignal(signal)
            .build();
        return command;
    }


    //Создаем команду в топик кафка tracking.master.command
    public Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, int version,
                                                                                   long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                                   Tracking.Portfolio.Action action, Tracking.Decimal price,
                                                                                   Tracking.Decimal quantityS, String ticker,
                                                                                   String tradingClearingAccount, String classCode, Double dynamicLimitQuantity,
                                                                                   Tracking.Decimal tailOrderQuantity) {
        UUID positionId = getInstrumentUID(ticker, classCode);
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .setPositionId(utilsTest.buildByteString(positionId))
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(price)
            .setQuantity(quantityS)
            .setTailOrderQuantity(tailOrderQuantity)
            .setDynamicLimitQuantity(com.google.protobuf.DoubleValue.newBuilder()
                .setValue(dynamicLimitQuantity).build())
            .build();
        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .setSignal(signal)
            .build();
        return command;
    }


    //Создаем команду в топик кафка tracking.master.command
    public Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandWithOutTailOrderQuantity(String contractId, OffsetDateTime time, int version,
                                                                                   long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                                   Tracking.Portfolio.Action action, Tracking.Decimal price,
                                                                                   Tracking.Decimal quantityS, String ticker, String tradingClearingAccount, String classCode, ByteString instrumentId){
        UUID positionId = getInstrumentUID(ticker, classCode);
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .setPositionId(utilsTest.buildByteString(positionId))
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio.BaseMoneyPosition baseMoneyPosition = Tracking.Portfolio.BaseMoneyPosition.newBuilder()
            .setQuantity(quantityBaseMoney)
            .build();
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        Tracking.Signal signal = Tracking.Signal.newBuilder()
            .setPrice(price)
            .setQuantity(quantityS)
            .setInstrumentId(instrumentId)
            .build();
        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .setSignal(signal)

            .build();
        return command;
    }


    @Step("Переместить offset топика {0} до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
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


    // создаем команду в топик кафка tracking.master.command
    public Tracking.PortfolioCommand createCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, long unscaled, int scale) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.INITIALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(1)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(Tracking.Decimal.newBuilder()
                        .setUnscaled(unscaled)
                        .setScale(scale)
                        .build())
                    .build())
                .build())
            .build();
        return command;
    }

    //метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создание подписки на стратегию strategyId: \"{strategyId}\" \n в статусе: {subscriptionStatus}, \n для контракта slaveContractId: {contractId}")
    public void createSubcription(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus, Boolean blocked,java.sql.Timestamp dateStart,
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
            .setEndTime(dateEnd)
            .setBlocked(blocked);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);
    }

    @Step("Получаем данные из сервиса счетов по siebelId: {0}")
    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    //Создаем команду в топик кафка tracking.master.command
    @Step("Создаем событие saveDevidend, для contractId: {contractId}")
    public Tracking.PortfolioCommand createSaveDividendMasterCommand (String contractId, OffsetDateTime time, long unscaled,
                                                                      int scale, String ticker, String tradingClearingAccount, Long dividendId, Tracking.Currency currency, BytesValue assetId) {
        int currencyValue;
        if (currency.equals(Tracking.Currency.USD)) {
            currencyValue = Tracking.Currency.USD_VALUE;
        }
        else {
            currencyValue = Tracking.Currency.RUB_VALUE;
        }
        Tracking.Decimal amount = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.ExchangePositionId exchangePositionId = Tracking.ExchangePositionId.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .build();
        Tracking.PortfolioCommand.Dividend dividend = Tracking.PortfolioCommand.Dividend.newBuilder()
            .setId(dividendId)
            .setExchangePositionId(exchangePositionId)
            .setAmount(amount)
            .setAssetId(assetId)
            .setCurrency(Tracking.Currency.valueOf(currencyValue))
            .build();
       Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setDividend(dividend)
            .build();
        allureSteps.stepForPortfolioCommand("Сформировали команду PortfolioCommand \n", command);
        return command;
    }

    @Step("Удаляем записи из strategy + contract + client")
    public void deleteDataFromDb (String SiebelId) {

        GetBrokerAccountsResponse getAllMasterAccounts = getALLAccountsFromAccount(SiebelId);
        UUID investId = getAllMasterAccounts.getInvestId();

        for(int i = 0; i < getAllMasterAccounts.getBrokerAccounts().size(); i++) {
            try {
                contractService.deleteContract(contractService.getContract(getAllMasterAccounts.getBrokerAccounts().get(i).getId()));
            } catch (Exception e) {}
        }
        try {
            clientService.deleteClient(clientService.getClient(investId));
        } catch (Exception e) {}
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


    public UUID getInstrumentUID(String ticker, String classCode){
        Response instrumentFireg = firegInstrumentsApiCreator.get().instrumentsInstrumentIdGet()
            .instrumentIdPath(ticker)
            .idKindQuery("ticker")
            .classCodeQuery(classCode)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String instrumentUID = instrumentFireg.getBody().jsonPath().getString("position_security_uid");
        return UUID.fromString(instrumentUID);
    }

}