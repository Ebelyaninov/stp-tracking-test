package ru.qa.tinkoff.steps.trackingFeeSteps;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
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
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;

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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

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
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient.api(ru.qa.tinkoff.swagger.MD.invoker
        .ApiClient.Config.apiConfig()).prices();
    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategyMaster;
    public Subscription subscription;
    public Contract contractSlave;
    public Client clientSlave;


    //Создаем в БД tracking данные по ведущему (Master-клиент): client, contract, strategy
    @Step("Создать договор и стратегию в бд автоследования для ведущего клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
       Map<String, BigDecimal> feeRate = new HashMap<>();
        feeRate.put("management", new BigDecimal("0.04"));
        feeRate.put("result",new BigDecimal("0.2"));
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
            .setFeeRate(feeRate);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
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
                                     int version, int comparedToMasterVersion, String money, List<SlavePortfolio.Position> positionList, Date date) {
        //создаем портфель master в cassandra
        //с базовой валютой
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, version,
            comparedToMasterVersion, baseMoneyPosition, positionList, date);
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
                                    Context context) {
        managementFeeDao.insertIntoManagementFee(contractIdSlave, strategyId, subscriptionId, version,
            settlementPeriodStartedAt,settlementPeriodEndedAt, context);
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






}
