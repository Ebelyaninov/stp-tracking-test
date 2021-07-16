//package stpTrackingConsumer.switchLimitConsumer;
//
//
//import com.google.protobuf.BytesValue;
//import com.google.protobuf.Timestamp;
//import com.google.protobuf.UnknownFieldSet;
//import extenstions.RestAssuredExtension;
//import io.qameta.allure.*;
//import io.qameta.allure.junit5.AllureJunit5;
//import io.restassured.response.ResponseBodyData;
//import limit.Limit;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.TestInstance;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
//import org.springframework.boot.test.context.SpringBootTest;
//import ru.qa.tinkoff.allure.Subfeature;
//import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
//import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
//import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
//import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
//import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
//import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
//import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
//import ru.qa.tinkoff.kafka.Topics;
//import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
//import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
//import ru.qa.tinkoff.swagger.miof.api.ClientApi;
//import ru.qa.tinkoff.swagger.miof.model.RuTinkoffTradingMiddlePositionsPositionsResponse;
//import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
//import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
//import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
//import ru.qa.tinkoff.tracking.entities.Contract;
//import ru.qa.tinkoff.tracking.entities.Strategy;
//import ru.qa.tinkoff.tracking.entities.Subscription;
//import ru.qa.tinkoff.tracking.entities.enums.*;
//import ru.qa.tinkoff.tracking.services.database.*;
//import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
//import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
//import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;
//import ru.tinkoff.invest.miof.Client;
//import ru.tinkoff.invest.miof.limits.Loading;
//import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
//import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufBytesReceiver;
//import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufCustomReceiver;
//import ru.tinkoff.invest.sdet.kafka.protobuf.sender.KafkaProtobufCustomSender;
//import ru.tinkoff.trading.tracking.Tracking;
//
//import javax.annotation.Resource;
//import java.math.BigDecimal;
//import java.time.*;
//import java.time.temporal.ChronoUnit;
//import java.util.*;
//
//import static io.qameta.allure.Allure.step;
//import static org.awaitility.Awaitility.await;
//import static org.hamcrest.CoreMatchers.not;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.Matchers.empty;
//import static org.hamcrest.Matchers.is;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;
//
//@Slf4j
//@Epic("switchLimitConsumer - Переключатель операции по обработке событий об изменении лимитов")
//@Feature("TAP-10220")
//@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
//@DisplayName("stp-tracking-consumer")
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@SpringBootTest(classes ={TrackingDatabaseAutoConfiguration.class,
//    KafkaAutoConfiguration.class,
//    InvestTrackingAutoConfiguration.class,
//    KafkaProtobufFactoryAutoConfiguration.class,
//    BillingDatabaseAutoConfiguration.class,
//    GrpcServicesAutoConfiguration.class
//})
//
//public class SwitchLimitConsumerTest {
//
//
//    @Resource(name = "customReceiverFactory")
//    KafkaProtobufCustomReceiver<String, byte[]> kafkaReceiver;
//    @Resource(name = "bytesReceiverFactory")
//    KafkaProtobufBytesReceiver<String, BytesValue> receiverBytes;
//    @Resource(name = "customSenderFactory")
//    KafkaProtobufCustomSender<String, byte[]> kafkaSender;
////
////
////    @Autowired
////    ClientService clientService;
////    @Autowired
////    ContractService contractService;
////    @Autowired
////    MasterPortfolioDao masterPortfolioDao;
////    @Autowired
////    SlavePortfolioDao slavePortfolioDao;
////    @Autowired
////    SlaveOrderDao slaveOrderDao;
////    @Autowired
////    StrategyService strategyService;
////    @Autowired
////    ExchangePositionService exchangePositionService;
////    @Autowired
////    TrackingService trackingService;
////    @Autowired
////    SubscriptionService subscriptionService;
////    @Autowired
////    MiddleGrpcService middleGrpcService;
////
////    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
////    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
////        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
////    ClientApi clientMiofApi = ru.qa.tinkoff.swagger.miof.invoker.ApiClient.api(ru.qa.tinkoff.swagger
////        .miof.invoker.ApiClient.Config.apiConfig()).client();
////    ru.qa.tinkoff.tracking.entities.Client clientMaster;
////    Contract contractMaster;
////    Strategy strategy;
////    ru.qa.tinkoff.tracking.entities.Client clientSlave;
////    Contract contractSlave;
////    Subscription subscription;
////    String contractIdMaster;
////    String contractIdSlave;
////    UUID strategyId;
////    String SIEBEL_ID_MASTER = "1-51Q76AT";
////
////    String ticker = "AAPL";
////    String tradingClearingAccount = "L01+00000SPB";
////
////
////
////    @AfterEach
////    void deleteClient() {
////        step("Удаляем клиента автоследования", () -> {
////            try {
////                subscriptionService.deleteSubscription(subscription);
////            } catch (Exception e) {
////            }
////            try {
////                contractService.deleteContract(contractSlave);
////            } catch (Exception e) {
////            }
////            try {
////                clientSlave = clientService.getClient(clientSlave.getId());
////            } catch (Exception e) {
////            }
////            try {
////                clientService.deleteClient(clientSlave);
////            } catch (Exception e) {
////            }
////            try {
////                trackingService.deleteStrategy(strategy);
////            } catch (Exception e) {
////            }
////            try {
////                contractService.deleteContract(contractMaster);
////            } catch (Exception e) {
////            }
////            try {
////                clientService.deleteClient(clientMaster);
////            } catch (Exception e) {
////            }
////            try {
////                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
////            } catch (Exception e) {
////            }
////            try {
////                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
////            } catch (Exception e) {
////            }
////            try {
////                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
////            } catch (Exception e) {
////            }
////            try {
////                createEventInTrackingEvent(contractIdSlave);
////            } catch (Exception e) {
////            }
////
////        });
////    }
//
//    @SneakyThrows
//    @Test
//    @AllureId("933558")
//    @DisplayName("C933558.HandleLimitEvent.Первичная инициализация портфеля, version из события = version из ответа," +
//            " формируем команду с измененными позициями")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
//    void C933558() {
////        String SIEBEL_ID_SLAVE = "1-EL3FQGQ";
////        String title = "тест стратегия autotest update currency";
////        String description = "description test стратегия autotest update adjust  currency";
////        //получаем данные по клиенту master в api сервиса счетов
////        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
////            .siebelIdPath(SIEBEL_ID_MASTER)
////            .brokerTypeQuery("broker")
////            .brokerStatusQuery("opened")
////            .respSpec(spec -> spec.expectStatusCode(200))
////            .execute(response -> response.as(GetBrokerAccountsResponse.class));
////        UUID investIdMaster = resAccountMaster.getInvestId();
////        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
////        //получаем данные по клиенту slave в БД сервиса счетов
////        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
////            .siebelIdPath(SIEBEL_ID_SLAVE)
////            .brokerTypeQuery("broker")
////            .brokerStatusQuery("opened")
////            .respSpec(spec -> spec.expectStatusCode(200))
////            .execute(response -> response.as(GetBrokerAccountsResponse.class));
////        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
////        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
////        strategyId = UUID.randomUUID();
//////      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
////        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
////            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
////            StrategyStatus.active, 0, LocalDateTime.now().minusDays(3));
////        //Создаем порфтель мастера  в cassandra
////        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
////        Date dateCreateMasPortfolio = Date.from(utc.toInstant());
////        createMasterPortfolioWithOutPos(1, "25000.0", dateCreateMasPortfolio);
////        //создаем подписку для slave
////        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
////        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
////        Client.GetClientPositionsReq clientPositionsReq = Client.GetClientPositionsReq.newBuilder()
////            .setAgreementId(contractIdSlave)
////            .build();
////        CapturedResponse<Client.GetClientPositionsResp> clientPositions =
////            middleGrpcService.getClientPositions(clientPositionsReq);
////        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
////        double middleQuantityBaseMoney =  getBaseMoneyFromMiddle( clientPositions, "RUB");
////        // складываем позиции по цб у которых kind =365 в map
////        OffsetDateTime utcNow = OffsetDateTime.now(ZoneOffset.UTC);
////        Date date = Date.from(utcNow.toInstant());
////        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
////        // создаем портфель slave с позицией в кассандре
////        positionListSl.add(SlavePortfolio.Position.builder()
////            .ticker("USD000UTSTOM")
////            .tradingClearingAccount("MB9885503216")
////            .quantity(new BigDecimal("0"))
////            .changedAt(date)
////            .build());
////        String baseMoneySl = String.valueOf(middleQuantityBaseMoney);
////        //с базовой валютой
////        SlavePortfolio.BaseMoneyPosition baseMoneyPositionSl = SlavePortfolio.BaseMoneyPosition.builder()
////            .quantity(new BigDecimal(baseMoneySl))
////            .changedAt(date)
////            .build();
////        //insert запись в cassandra
////        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, versionMiddle, 1,
////            baseMoneyPositionSl, positionListSl, date);
////
////        // вычитываем из топика кафка tracking.slave.command все offset
////        resetOffsetToLate(TRACKING_SLAVE_COMMAND);
////        //вызываем метод middleOffice по изменению позиции клиента по валюте
////        clientMiofApi.clientAdjustCurrencyGet()
////            .typeQuery("Withdraw")
////            .amountQuery(1).currencyQuery("RUB")
////            .clientCodeQuery(clientCodeSlave)
////            .respSpec(spec -> spec.expectStatusCode(200))
////            .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
////
////        //смотрим, сообщение, которое поймали в топике kafka tracking.slave.command
////        Optional<Map<String, byte[]>> first = await().atMost(Duration.ofSeconds(20))
////            .until(
////                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND.getName()), is(empty())
////            ).stream().findFirst();
////        assertFalse(first.isPresent());
//
//
//
//
//
//
//
//
//
//
//
//        LocalDate before = LocalDate.of(1970, Month.JANUARY, 1);
//        LocalDate after = LocalDate.now();
//        long days = ChronoUnit.DAYS.between(before, after);
//        int loadDate = Math.toIntExact(days);
//        OffsetDateTime now = OffsetDateTime.now();
//
//        Loading.Event event = Loading.Event.newBuilder()
//            .setLimitLoadingInfo(Loading.LimitLoadingInfo.newBuilder()
//                    .setId(601)
//                    .setStatus(Loading.Status.COMPLETED)
//                    .setStartAt(Timestamp.newBuilder()
//                        .setSeconds(now.toEpochSecond())
//                        .setNanos(now.getNano())
////                        .setUnknownFields(UnknownFieldSet.newBuilder()
////                            .build())
//                        .build())
//                    .setEndAt(Timestamp.newBuilder().build())
//                    .setLoadDate(loadDate)
////                    .setUnknownFields(UnknownFieldSet.newBuilder()
////                        .build())
//                )
////                .setUnknownFields(UnknownFieldSet.newBuilder().build())
//            .build();
//        log.info("Событие  в miof.limit-loading-info:  {}", event);
//
//       byte[] eventBytes = event.toByteArray();
//            //отправляем событие в топик miof.positions.raw
//        kafkaSender.send("miof.limit-loading-info", eventBytes);
//
//
//
//
//        }
//
//
//
////
////
////    //метод создает клиента, договор и стратегию в БД автоследования
////    void createClientWintContractAndStrategy(String SIEBLE_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
////                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
////                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
////                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
////        //создаем запись о клиенте в tracking.client
////        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null);
////        // создаем запись о договоре клиента в tracking.contract
////        contractMaster = new Contract()
////            .setId(contractId)
////            .setClientId(clientMaster.getId())
////            .setRole(contractRole)
////            .setState(contractState)
////            .setStrategyId(null)
////            .setBlocked(false);
////
////        contractMaster = contractService.saveContract(contractMaster);
////        //создаем запись о стратегии клиента
////        strategy = new Strategy()
////            .setId(strategyId)
////            .setContract(contractMaster)
////            .setTitle(title)
////            .setBaseCurrency(strategyCurrency)
////            .setRiskProfile(strategyRiskProfile)
////            .setDescription(description)
////            .setStatus(strategyStatus)
////            .setSlavesCount(slaveCount)
////            .setActivationTime(date)
////            .setScore(1);
////
////        strategy = trackingService.saveStrategy(strategy);
////    }
////
////    //создаем пустой портфель master в cassandra после инициализации
////    void createMasterPortfolioWithOutPos( int version, String baseMoney, Date date ) {
////        //c позицией по бумаге
////        List<MasterPortfolio.Position> positionList = new ArrayList<>();
////        //с базовой валютой
////        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
////            .quantity(new BigDecimal(baseMoney))
////            .changedAt(date)
////            .build();
////        //insert запись в cassandra
////        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
////    }
////
////    //вызываем метод CreateSubscription для slave
////    void createSubscriptionSlave(String siebleIdSlave, String contractIdSlave, UUID strategyId) {
////        subscriptionApi.createSubscription()
////            .xAppNameHeader("invest")
////            .xAppVersionHeader("4.5.6")
////            .xPlatformHeader("ios")
////            .xTcsSiebelIdHeader(siebleIdSlave)
////            .contractIdQuery(contractIdSlave)
////            .strategyIdPath(strategyId)
////            .respSpec(spec -> spec.expectStatusCode(200))
////            .execute(ResponseBodyData::asString);
////        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
////        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
////        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
////        contractSlave = contractService.getContract(contractIdSlave);
////    }
////
////    double getBaseMoneyFromMiddle(CapturedResponse<Client.GetClientPositionsResp> clientPositions, String curBaseMoney) {
////        double middleQuantityBaseMoney = 0;
////        //складываем позиции по валютам у которых kind =365 в map, в отдельную map складем базовую валюту и ее значение
////        for (int i = 0; i < clientPositions.getResponse().getClientPositions().getMoneyCount(); i++) {
////            //значение по базовой валюте кладем в middleQuantityBaseMoney
////            if ("T365".equals(clientPositions.getResponse().getClientPositions().getMoney(i).getKind().name())
////                && (curBaseMoney.equals(clientPositions.getResponse().getClientPositions().getMoney(i).getCurrency()))
////            ) {
////                middleQuantityBaseMoney = (clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getUnscaled()
////                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBalance().getScale()))
////                    +  (clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getUnscaled()
////                    * Math.pow(10, -1 * clientPositions.getResponse().getClientPositions().getMoney(i).getBlocked().getScale()));
////            }
////
////        }
////        return  middleQuantityBaseMoney;
////
////    }
////
////
////    @Step("Переместить offset до текущей позиции")
////    public void resetOffsetToLate(Topics topic) {
////        log.info("Полечен запрос на вычитавание всех сообщений из Kafka топика {} ", topic.getName());
////        await().atMost(Duration.ofSeconds(30))
////            .until(() -> receiverBytes.receiveBatch(topic.getName(),
////                Duration.ofSeconds(3), BytesValue.class), List::isEmpty);
////        log.info("Все сообщения из {} топика вычитаны", topic.getName());
////    }
////
////    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
////    void createEventInTrackingEvent(String contractIdSlave)  {
////        //создаем событие
////        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
////        log.info("Команда в tracking.event:  {}", event);
////        //кодируем событие по protobuff схеме и переводим в byteArray
////        byte[] eventBytes = event.toByteArray();
////        //отправляем событие в топик kafka tracking.slave.command
////        kafkaSender.send("tracking.event", contractIdSlave, eventBytes);
////    }
////
////    // создаем команду в топик кафка tracking.event
////    Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId) {
////        OffsetDateTime now = OffsetDateTime.now();
////        Tracking.Event event = Tracking.Event.newBuilder()
////            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
////            .setAction(Tracking.Event.Action.UPDATED)
////            .setCreatedAt(Timestamp.newBuilder()
////                .setSeconds(now.toEpochSecond())
////                .setNanos(now.getNano())
////                .build())
////            .setContract(Tracking.Contract.newBuilder()
////                .setId(contractId)
////                .setState(Tracking.Contract.State.TRACKED)
////                .setBlocked(false)
////                .build())
////            .build();
////        return event;
////    }
////
//
//
//}
