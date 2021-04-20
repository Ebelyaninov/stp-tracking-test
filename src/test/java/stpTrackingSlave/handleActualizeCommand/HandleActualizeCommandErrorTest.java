package stpTrackingSlave.handleActualizeCommand;

import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufBytesReceiver;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufCustomReceiver;
import ru.tinkoff.invest.sdet.kafka.protobuf.sender.KafkaProtobufCustomSender;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию")
@Feature("TAP-6864")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaProtobufFactoryAutoConfiguration.class,
    ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration.class
})
public class HandleActualizeCommandErrorTest {
    @Resource(name = "customSenderFactory")
    KafkaProtobufCustomSender<String, byte[]> kafkaSender;
    @Resource(name = "bytesReceiverFactory")
    KafkaProtobufBytesReceiver<String, BytesValue> receiverBytes;
    @Resource(name = "customReceiverFactory")
    KafkaProtobufCustomReceiver<String, byte[]> kafkaReceiver;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    CacheApi cacheApi = ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.api(ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.Config.apiConfig()).cache();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    Contract contract;
    String contractIdMaster;
    String ticker = "AAPL";
    String tradingClearingAccount = "L01+00000SPB";
    String contractIdSlave = "2039667312";
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-AJ7L9FNI";
    String SIEBEL_ID_SLAVE = "5-1HE55RPOV";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("731505")
    @DisplayName("C731505.HandleActualizeCommand.Запись в кэше contractCache по contractId = contract_id не найдена")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731505() {
        String contractIdSlave = "2054285965";
        UUID strategyId = UUID.randomUUID();
//        //формируем команду на актуализацию для slave
//        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave, 2);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        kafkaSender.send("tracking.slave.command", contractIdSlave, eventBytes);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("731507")
    @DisplayName("C731507.HandleActualizeCommand.Статус договора state != 'tracked' И blocked = false")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731507() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
//        //формируем команду на актуализацию для slave
//        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdMaster, 2);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        kafkaSender.send("tracking.slave.command", contractIdMaster, eventBytes);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("731508")
    @DisplayName("C731508.HandleActualizeCommand.Статус договора state = 'tracked' И blocked = true")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731508() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(positionListMaster, 3, "6551.10");
        //создаем подписку на стратегию для lave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем команду для топика tracking.event
        createEventInTrackingEventBlock(contractIdSlave, true);
        contract = contractService.updateBlockedContract(contractIdSlave, true);
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave, 2);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        kafkaSender.send("tracking.slave.command", contractIdSlave, eventBytes);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("731545")
    @DisplayName("C731545.HandleActualizeCommand.Портфель мастера не найден")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731545() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку на стратегию для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //вычитываем все события из tracking.event
        resetOffsetToLate(TRACKING_EVENT);
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave, 2);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        kafkaSender.send("tracking.slave.command", contractIdSlave, eventBytes);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        Map<String, byte[]> messageDelay = await().atMost(Duration.ofSeconds(31))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_EVENT.getName()), is(not(empty()))
            ).stream().findFirst().orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(messageDelay.values().stream().findAny().get());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("742690")
    @DisplayName("C742690.HandleActualizeCommand.Подтверждение по отклоненной заявке")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C742690() {
        String SIEBEL_ID_SLAVE = "5-1HE55RPOV";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        String ticker = "AAPL";
        String classCode = "SPBXM";
        String tradingClearingAccount = "L01+00000SPB";
        BigDecimal lot = new BigDecimal("1");
        //отправляем в топик tracking.test.md.prices.int.stream данные по ценам на бумагу: last, ask, bid
        createDataToMarketData(ticker, classCode, "107.97", "108.17", "108.06");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID_MASTER)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(positionListMaster, 3, "6551.10");
        //создаем подписку на стратегию для slave
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal("0"))
            .price(new BigDecimal("107.79"))
            .price_ts(date)
            .rate(new BigDecimal("0"))
            .rateDiff(new BigDecimal("0.0751"))
            .quantityDiff(new BigDecimal("5"))
            .changedAt(date)
            .build());
        String baseMoneySl = "7000.0";
        createSlavePortfolio(2, 3, null, baseMoneySl, positionListSl);
        slaveOrderDao.insertIntoSlaveOrder(contractIdSlave, strategyId, 2, 1,
            0, classCode, UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            (byte) 0, ticker, tradingClearingAccount);
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(1)
            .setUnscaled(58556)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(5).build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(2)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .build())
                .addPosition(position)
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из tracking.event
        resetOffsetToLate(TRACKING_EVENT);
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send("tracking.slave.command", contractIdSlave, eventBytes);
        Thread.sleep(5000);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(getPriceFromExchangePositionPriceCache(ticker, "last"));
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal baseMoneySlave = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slaveQuantity = slavePortfolio.getPositions().get(0).getQuantity();
        BigDecimal slavePosQuantity = slaveQuantity.multiply(price);
        BigDecimal slavePortfolioValue = slavePosQuantity.add(baseMoneySlave);
        BigDecimal slavePositionRate = slavePosQuantity.divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal quantityDiff = (rateDiff.multiply(slavePortfolioValue)).divide(price, 0, BigDecimal.ROUND_HALF_UP);
        assertThat("Направление заявки Action не равно", slavePortfolio.getVersion(), is(2));
        assertThat("Направление заявки Action не равно", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Направление заявки Action не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5855.6"));
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getQuantity().toString(), is("5"));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(0).getQuantityDiff(), is(quantityDiff));
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("найдена запись в masterPortfolio", slaveOrder.getState().toString(), is("0"));
        //смотрим, сообщение, которое поймали в топике kafka tracking.event
        Map<String, byte[]> messageDelay = await().atMost(Duration.ofSeconds(10))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_EVENT.getName()), is(not(empty()))
            ).stream().findFirst().orElseThrow(() -> new RuntimeException("Сообщений не получено"));

        Tracking.Event event = Tracking.Event.parseFrom(messageDelay.values().stream().findAny().get());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("ID события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID договора не равен", event.getContract().getState().toString(), is("TRACKED"));
        assertThat("ID стратегии не равен", (event.getContract().getBlocked()), is(true));

        //находим в БД автоследования contract
        contract = contractService.getContract(contractIdSlave);
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("tracked"));
        assertThat("статус клиента не равно", (contract.getBlocked()), is(true));

    }

    // методы для работы тестов*************************************************************************
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

    Tracking.Event createEventUpdateAfterSubscriptionSlaveBlock(String contractId, boolean blocked) {
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


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send("tracking.event", contractIdSlave, eventBytes);
    }

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEventBlock(String contractIdSlave, boolean blocked) throws InterruptedException {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlaveBlock(contractIdSlave, blocked);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send("tracking.event", contractIdSlave, eventBytes);


    }

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(String SIEBLE_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
            .setScore(1);


        strategy = trackingService.saveStrategy(strategy);
    }


    //вызываем метод CreateSubscription для slave
    void createSubscriptionSlave(String siebleIdSlave, String contractIdSlave, UUID strategyId) {
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


    void createMasterPortfolio(List<MasterPortfolio.Position> positionList, int version, String money) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPosition, positionList);
    }

    //создаем портфель master в cassandra
    void createSlavePortfolio(int version, int comparedToMasterVersion, String currency, String money,
                              List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolio(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList);
    }


    public String getPriceFromExchangePositionPriceCache(String ticker, String type) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache по певой ноде
        Response resCacheNodeZero = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("magic-number", "0"))
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


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        Integer synchronizedToMasterVersion, BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal masterPositionRate, BigDecimal quantityDiff) {
        ;
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("SynchronizedToMasterVersion позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getSynchronizedToMasterVersion(), is(synchronizedToMasterVersion));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(masterPositionRate));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }


    // отправляем событие в tracking.test.md.prices.int.stream
    public void createEventTrackingTestMdPricesInStream(String instrumentId, String type, String oldPrice, String newPrice) {
        String event = PriceUpdatedEvent.getKafkaTemplate(LocalDateTime.now(ZoneOffset.UTC), instrumentId, type, oldPrice, newPrice);
        String key = PriceUpdatedKey.getKafkaTemplate(instrumentId);
        //отправляем событие в топик kafka tracking.test.md.prices.int.stream
        stringSenderService.send(Topics.TRACKING_TEST_MD_PRICES_INT_STREAM, key, event);


    }

    void createDataToMarketData(String ticker, String classCode, String lastPrice, String askPrice, String bidPrice) {
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "last", lastPrice, "107.97");
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "ask", askPrice, "108.17");
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "bid", bidPrice, "108.06");
    }


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal rateDiff, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }


    Tracking.PortfolioCommand createCommandActualizeOnlyBaseMoney(int scale, int unscaled, String contractIdSlave, int version) {
        OffsetDateTime time = OffsetDateTime.now();
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .build())
                .build())
            .build();
        return command;
    }

    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Полечен запрос на вычитавание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> receiverBytes.receiveBatch(topic.getName(),
                Duration.ofSeconds(3), BytesValue.class), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }
}
