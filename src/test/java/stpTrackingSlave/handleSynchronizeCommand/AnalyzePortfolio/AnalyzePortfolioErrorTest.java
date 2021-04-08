package stpTrackingSlave.handleSynchronizeCommand.AnalyzePortfolio;


import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("handleSynchronizeCommand -Анализ портфеля и фиксация результата")
@Feature("TAP-7930")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class, InvestTrackingAutoConfiguration.class
})
public class AnalyzePortfolioErrorTest {
    KafkaHelper kafkaHelper = new KafkaHelper();
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
    ExchangePositionApi exchangePositionApi;
    StrategyApi strategyApi;
    SignalApi signalApi;
    SubscriptionApi subscriptionApi;
    CacheApi cacheApi;
    SlavePortfolio slavePortfolio;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-T0Q1FNE0";
    String SIEBEL_ID_SLAVE = "4-1O6RYOAP";

    @BeforeAll
    void conf() {
        strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
        signalApi = ApiClient.api(ApiClient.Config.apiConfig()).signal();
        subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
        exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
        cacheApi = ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.api(ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.Config.apiConfig()).cache();
    }

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
    @AllureId("681110")
    @DisplayName("C681110.AnalyzePortfolio.Анализ портфеля.Позиция не найдена в exchangePositionCache")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C681110() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolio("TEST", "L01+00000F00", "2.0", 2, 2,
            positionAction, "2259.17");
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "3657.23";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        Thread.sleep(5000);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(0));
    }

    @SneakyThrows
    @Test
    @AllureId("682320")
    @DisplayName("C682320.AnalyzePortfolio.Анализ портфеля. Позиции значение currency != strategy.base_currency")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C682320() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolio("RU000A0JXPU3", "L01+00000F00", "2.0", 2, 2,
            positionAction, "2259.17");
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "657.23";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        Thread.sleep(5000);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(0));
    }


    @SneakyThrows
    @Test
    @AllureId("682333")
    @DisplayName("C682333.AnalyzePortfolio.Анализ портфеля.Не найдена цена позиции price_type = 'last' в кеш exchangePositionPriceCache")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C682333() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolio("SMG", "L01+00000SPB", "2.0", 2, 2,
            positionAction, "2259.17");
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        String baseMoneySlave = "3657.23";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave);
        //включаем kafka - consumer для топика tracking.event
        Tracking.Event event = null;
        LocalDateTime dateCreateTr = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        try (KafkaMessageConsumer<byte[], byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.event",
                     ByteArrayDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //отправляем команду на синхронизацию
            createCommandSynTrackingSlaveCommand(contractIdSlave);
            //ловим команду, в топике kafka tracking.event
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<byte[], byte[]>> records = messageConsumer.listRecords();
            for (int i = 0; i < records.size(); i++) {
                Tracking.Event eventBefore = Tracking.Event.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(eventBefore.getContract().getId()))
                    & ("UPDATED".equals(eventBefore.getAction().toString()))
                    & (eventBefore.getContract().getBlocked() != false)) {
                    event = eventBefore;
                    break;
                }
            }
            log.info("Событие  в tracking.event:  {}", event);
        }

        //проверяем, данные в сообщении
        assertThat("action события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        assertThat("статус договора не равен", (event.getContract().getState()), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(true));

        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия последнего портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Версия последнего портфеля ведущего не равна", slavePortfolio.getComparedToMasterVersion(), is(1));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Размер позиций slave не равна", slavePortfolio.getPositions().size(), is(0));
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

    Tracking.PortfolioCommand createCommandSynchronize(String contractIdSlave) {
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
    void createEventInTrackingEvent(String contractIdSlave) throws InterruptedException {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        String key = contractIdSlave;
        //отправляем событие в топик kafka tracking.event
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.event");
        template.sendDefault(key, eventBytes);
        template.flush();
        Thread.sleep(10000);
    }


    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandSynTrackingSlaveCommand(String contractIdSlave) throws InterruptedException {
        //создаем команду
        Tracking.PortfolioCommand command = createCommandSynchronize(contractIdSlave);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuff схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdSlave;
        //отправляем команду в топик kafka tracking.master.command
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.slave.command");
        template.sendDefault(keyMaster, eventBytes);
        template.flush();
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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


    void createMasterPortfolio(String ticker, String tradingClearingAccount, String quantityPos,
                               int lastChangeDetectedVersion, int version,
                               Tracking.Portfolio.Position position,
                               String money) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());

        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, version, baseMoneyPosition, positionList);
    }

    void createSlavePortfolioWithOutPosition(int version, int comparedToMasterVersion, String currency, String money) {
        //создаем портфель master в cassandra
        //c позицией по бумаге
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
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
}
