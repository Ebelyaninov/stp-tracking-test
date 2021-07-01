package stpTrackingConsumer.handleLimitEvent.spy;

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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.entities.ClientCode;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.swagger.miof.api.ClientApi;
import ru.qa.tinkoff.swagger.miof.invoker.ApiClient;
import ru.qa.tinkoff.swagger.miof.model.InlineResponse20014;
import ru.qa.tinkoff.swagger.miof.model.RuTinkoffTradingMiddlePositionsPositionsResponse;
import ru.qa.tinkoff.swagger.miof.model.RuTinkoffTradingMiddlePositionsSimpleMoneyPosition;
import ru.qa.tinkoff.swagger.miof.model.RuTinkoffTradingMiddlePositionsSimpleSecurityPosition;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.SubscriptionService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("handleLimitEvent - Обработка событий об изменении позиций (spy)")
@Feature("TAP-8362")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-consumer-spy")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, SocialDataBaseAutoConfiguration.class, InvestTrackingAutoConfiguration.class})


public class HandleLimitEventSpySlaveTest {
    KafkaHelper kafkaHelper = new KafkaHelper();
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    TrackingService trackingService;
    ClientApi clientMiofApi;
    StrategyApi strategyApi;
    SubscriptionApi subscriptionApi;
    ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi strategyApiAdmin;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;


    @BeforeAll
    void conf() {
        clientMiofApi = ru.qa.tinkoff.swagger.miof.invoker.ApiClient.api(ApiClient.Config.apiConfig()).client();
        strategyApi = ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.Config.apiConfig()).strategy();
        subscriptionApi = ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.Config.apiConfig()).subscription();
        strategyApiAdmin = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).strategy();
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
        });
    }

    String contractIdSlave;
    UUID strategyId;
    UUID clientIdSlave;


    @SneakyThrows
    @Test
    @AllureId("641029")
    @DisplayName("C641029.HandleLimitEvent.Формирование команды об изменении ценной бумаги")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C641029()  {
        String SIEBEL_ID_MASTER = "1-5EEFOQV";
        String SIEBEL_ID_SLAVE = "5-3G0I9EP9";
        String title = "тест стратегия autotest update adjust security";
        String description = "description test стратегия autotest update adjust security";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        String contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        clientIdSlave = findValidAccountWithSiebleIdSlave.get(0).getInvestAccount().getId();
        List<ClientCode> findClientCodeByContractIdSlave = billingService.getFindClientCodeByBrokerAccountId(contractIdSlave);
        String clientCodeSlave = findClientCodeByContractIdSlave.get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель slave в cassandra
        //c позицией по бумаге XS0743596040
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker("XS0743596040")
            .tradingClearingAccount("L01+00002F00")
            .quantity(new BigDecimal("12"))
            .build());
        //с базовой валютой
        BigDecimal baseMoney = new BigDecimal("34000.0");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(baseMoney)
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolio(contractIdSlave, strategyId, 1, 1, baseMoneyPosition,  positionList);
        String key = null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по ценной бумаге
            clientMiofApi.clientAdjustSecurityGet()
                .quantityQuery(1)
                .tickerQuery("FXIT")
                .clientCodeQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
            messageConsumer.setTimeout(5000);
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
            //находим в полученных consumer messages, команду по contractIdSlave с операцией ACTUALIZE
            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                    portfolioCommand = portfolioCommandBefore;
                    key = records.get(i).key;
                    break;
                }
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //вызываем метод middleOffice, который возвращает список позиций клиента
        InlineResponse20014 expecResponsePos =
            clientMiofApi.clientPositionsGet()
                .agrNumQuery(contractIdSlave)
                .clientIdQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(InlineResponse20014.class));
        //находим значение balance и locked по бумаге FXIT и базовой валюте в ответе от rest middleOffice
        double balanceSer = 0.0;
        double lockedSer = 0.0;
        for (RuTinkoffTradingMiddlePositionsSimpleSecurityPosition simpleSecuritiesPosition : expecResponsePos.getPayload().getSecurities()) {
            if ("365".equals(simpleSecuritiesPosition.getKind())
                && "FXIT".equals(simpleSecuritiesPosition.getTicker())) {
                balanceSer = simpleSecuritiesPosition.getBalance();
                lockedSer = simpleSecuritiesPosition.getBlocked();
            }
        }
        double balanceCur = 0.0;
        double lockedCur = 0.0;
        //находим balance и locked по базовой валюте
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCur = simpleMoneyPosition.getBalance().getValue();
                lockedCur = simpleMoneyPosition.getBlocked().getValue();
            }
        }
        double quantityMiddleSer = balanceSer + lockedSer;
        double quantityMiddleCur = balanceCur + lockedCur;
        //находим в команде portfolioCommand позицию, по которой делали изменения в middle
        String ticker = null;
        String tradingClearingAccount = null;
        String action = null;
        double unscaled = 0.0;
        double scale = 0.0;
        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
            if ("FXIT".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
            }
        }
        double quantityTrackingSer = unscaled * Math.pow(10, -1 * scale);
        double quantityTrackingCur = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("ticker не равен", ticker, is("FXIT"));
        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("Y02+00001F00"));
        assertThat("action  не равен", action, is("ADJUST_SECURITY"));
        assertThat("quantity по бумагам  не равен", (quantityTrackingSer), is(quantityMiddleSer));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(true));
        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCur), is(quantityMiddleCur));
    }


    @SneakyThrows
    @Test
    @AllureId("621325")
    @DisplayName("C621325.HandleLimitEvent.Формирование команды об изменении валютной позиции")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C621325() throws Exception {
        String SIEBEL_ID_MASTER = "1-5EEFOQV";
        String SIEBEL_ID_SLAVE = "5-1ZQU2OP5G";
        String title = "тест стратегия autotest update adjust currency";
        String description = "description test стратегия autotest update adjust currency";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        String contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        clientIdSlave = findValidAccountWithSiebleIdSlave.get(0).getInvestAccount().getId();
        List<ClientCode> findClientCodeByContractIdSlave = billingService.getFindClientCodeByBrokerAccountId(contractIdSlave);
        String clientCodeSlave = findClientCodeByContractIdSlave.get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель slave в cassandra
        //добавляем позицию по валюте EUR в портфель
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        positionList.add(SlavePortfolio.Position.builder()
            .ticker("EUR_RUB__TOM")
            .tradingClearingAccount("MB9885503216")
            .build());
        //insert в slavePortfolio cassandra
        slavePortfolioDao.insertIntoSlavePortfolio(contractIdSlave, strategyId, 1, 1, null, positionList);
        String key = null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по валюте
            clientMiofApi.clientAdjustCurrencyGet()
                .typeQuery("Withdraw")
                .amountQuery(1).currencyQuery("USD")
                .clientCodeQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
            messageConsumer.setTimeout(5000);
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
            //парсим команду: получаем key, value, достаем значение created_at из value
            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                    portfolioCommand = portfolioCommandBefore;
                    key = records.get(i).key;
                    break;
                }
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //вызываем метод middleOffice, который возвращает список позиций клиента
        InlineResponse20014 expecResponsePos =
            clientMiofApi.clientPositionsGet()
                .agrNumQuery(contractIdSlave)
                .clientIdQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(InlineResponse20014.class));
        //находим значение balance и locked по валюте
        double balanceCur = 0.0;
        double lockedCur = 0.0;
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "USD".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCur = simpleMoneyPosition.getBalance().getValue();
                lockedCur = simpleMoneyPosition.getBlocked().getValue();
            }
        }
        double balanceCurBase = 0.0;
        double lockedCurBase = 0.0;
        //находим balance и locked по базовой валюте
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCurBase = simpleMoneyPosition.getBalance().getValue();
                lockedCurBase = simpleMoneyPosition.getBlocked().getValue();
            }
        }
        double quantityMiddleCur = balanceCur + lockedCur;
        double quantityMiddleCurBase = balanceCurBase + lockedCurBase;
        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
        String ticker = null;
        String tradingClearingAccount = null;
        String action = null;
        double unscaled = 0.0;
        double scale = 0.0;
        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
            if ("USD000UTSTOM".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
                break;
            }
        }
        double quantityTrackingCur = unscaled * Math.pow(10, -1 * scale);
        double quantityTrackingCurBase = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("ticker не равен", ticker, is("USD000UTSTOM"));
        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("MB9885503216"));
        assertThat("action  не равен", action, is("ADJUST_CURRENCY"));
        assertThat("quantity по валюте  не равен", (quantityTrackingCur), is(quantityMiddleCur));
        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCurBase), is(quantityMiddleCurBase));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("636045")
    @DisplayName("C636045.HandleLimitEvent.Найдена позиция по базовой валюте, параметр base_money_position в событии, c портфелем в cassandra")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C636045() throws Exception {
        String SIEBEL_ID_MASTER = "1-5EEFOQV";
        String SIEBEL_ID_SLAVE = "5-HMT64GGA";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        String contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        clientIdSlave = findValidAccountWithSiebleIdSlave.get(0).getInvestAccount().getId();
        List<ClientCode> findClientCodeByContractIdSlave = billingService.getFindClientCodeByBrokerAccountId(contractIdSlave);
        String clientCodeSlave = findClientCodeByContractIdSlave.get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель в cassandra
        //добавляем параметр базовая валюта
        BigDecimal baseMoney = new BigDecimal("32000.0");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(baseMoney)
            .changedAt(date)
            .build();
        //insert записи о портфеле в slavePortfolio
        slavePortfolioDao.insertIntoSlavePortfolio(contractIdSlave, strategyId, 1, 1, baseMoneyPosition, null);
        String key = null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по базовой валюте
            clientMiofApi.clientAdjustCurrencyGet()
                .typeQuery("Withdraw")
                .amountQuery(1).currencyQuery("RUB")
                .clientCodeQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
            //ловим команду, в топике kafka tracking.master.command
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
            //парсим команду: получаем key, value, достаем значение created_at из value
            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                    portfolioCommand = portfolioCommandBefore;
                    key = records.get(i).key;
                    break;
                }
            }
        }
        //приводим значение created_at к нужному формату до минут
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //вызываем метод middleOffice, который возвращает список позиций клиента
        InlineResponse20014 expecResponsePos =
            clientMiofApi.clientPositionsGet()
                .agrNumQuery(contractIdSlave)
                .clientIdQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(InlineResponse20014.class));
        double balanceCur = 0.0;
        double lockedCur = 0.0;
        //находим balance и locked по базовой валюте
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCur = simpleMoneyPosition.getBalance().getValue();
                lockedCur = simpleMoneyPosition.getBlocked().getValue();
                break;
            }
        }
        double quantityMiddleCur = balanceCur + lockedCur;
        double quantityTrackingCur = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(true));
        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCur), is(quantityMiddleCur));
    }


    @SneakyThrows
    @Test
    @AllureId("621220")
    @DisplayName("C621220.HandleLimitEvent.Отсутствует позиция по базовой валюте, параметр base_money_position в событии")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C621220() throws Exception {
        String SIEBEL_ID_MASTER = "1-5EEFOQV";
        String SIEBEL_ID_SLAVE = "5-BZIS073O";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        LocalDateTime dateCreateTr = null;
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        String contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        clientIdSlave = findValidAccountWithSiebleIdSlave.get(0).getInvestAccount().getId();
        List<ClientCode> findClientCodeByContractIdSlave = billingService.getFindClientCodeByBrokerAccountId(contractIdSlave);
        String clientCodeSlave = findClientCodeByContractIdSlave.get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String key = null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по ценной бумаге
            clientMiofApi.clientAdjustSecurityGet()
                .quantityQuery(1)
                .tickerQuery("FXIT")
                .clientCodeQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
            //ловим команду, в топике kafka tracking.master.command
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
            //парсим команду: получаем key, value, достаем значение created_at из value
            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                    portfolioCommand = portfolioCommandBefore;
                    key = records.get(i).key;
                    break;
                }
            }
        }
        //приводим значение created_at к нужному формату до минут
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //вызываем метод middleOffice, который возвращает список позиций клиента
        InlineResponse20014 expecResponsePos =
            clientMiofApi.clientPositionsGet()
                .agrNumQuery(contractIdSlave)
                .clientIdQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(InlineResponse20014.class));
        //находим значение balance и locked по бумаге FXIT
        double balanceSer = 0.0;
        double lockedSer = 0.0;
        for (RuTinkoffTradingMiddlePositionsSimpleSecurityPosition simpleSecuritiesPosition : expecResponsePos.getPayload().getSecurities()) {
            if ("365".equals(simpleSecuritiesPosition.getKind())
                && "FXIT".equals(simpleSecuritiesPosition.getTicker())) {
                balanceSer = simpleSecuritiesPosition.getBalance();
                lockedSer = simpleSecuritiesPosition.getBlocked();
            }
        }
        double balanceCur = 0.0;
        double lockedCur = 0.0;
        //находим balance и locked по базовой валюте
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCur = simpleMoneyPosition.getBalance().getValue();
                lockedCur = simpleMoneyPosition.getBlocked().getValue();
            }
        }
        double quantityMiddleSer = balanceSer + lockedSer;
        double quantityMiddleCur = balanceCur + lockedCur;
        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
        String ticker = null;
        String tradingClearingAccount = null;
        String action = null;
        double unscaled = 0.0;
        double scale = 0.0;
        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
            if ("FXIT".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
            }
        }
        double quantityTrackingSer = unscaled * Math.pow(10, -1 * scale);
        double quantityTrackingCur = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("ticker не равен", ticker, is("FXIT"));
        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("Y02+00001F00"));
        assertThat("action  не равен", action, is("ADJUST_SECURITY"));
        assertThat("quantity по бумагам  не равен", (quantityTrackingSer), is(quantityMiddleSer));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCur), is(quantityMiddleCur));
    }


    @SneakyThrows
    @Test
    @AllureId("649738")
    @DisplayName("C649738.HandleLimitEvent.Найдена позиция по базовой валюте, параметр base_money_position в событии, без портфеля в cassandra")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C649738() throws Exception {
        String SIEBEL_ID_MASTER = "1-5EEFOQV";
        String SIEBEL_ID_SLAVE = "4-UA487IB";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        String contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        clientIdSlave = findValidAccountWithSiebleIdSlave.get(0).getInvestAccount().getId();
        List<ClientCode> findClientCodeByContractIdSlave = billingService.getFindClientCodeByBrokerAccountId(contractIdSlave);
        String clientCodeSlave = findClientCodeByContractIdSlave.get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String key = null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по базовой валюте
            clientMiofApi.clientAdjustCurrencyGet()
                .typeQuery("Withdraw")
                .amountQuery(1).currencyQuery("RUB")
                .clientCodeQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
            //ловим команду, в топике kafka tracking.master.command
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
            //парсим команду: получаем key, value, достаем значение created_at из value
            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                    portfolioCommand = portfolioCommandBefore;
                    key = records.get(i).key;
                    break;
                }
            }
        }
        //приводим значение created_at к нужному формату до минут
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //вызываем метод middleOffice, который возвращает список позиций клиента
        InlineResponse20014 expecResponsePos =
            clientMiofApi.clientPositionsGet()
                .agrNumQuery(contractIdSlave)
                .clientIdQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(InlineResponse20014.class));
        double balanceCur = 0.0;
        double lockedCur = 0.0;
        //находим balance и locked по базовой валюте
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCur = simpleMoneyPosition.getBalance().getValue();
                lockedCur = simpleMoneyPosition.getBlocked().getValue();
                break;
            }
        }
        double quantityMiddleCur = balanceCur + lockedCur;
        double quantityTrackingCur = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(true));
        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCur), is(quantityMiddleCur));
    }


    @SneakyThrows
    @Test
    @AllureId("621173")
    @DisplayName("C621173.HandleLimitEvent.money_limit.currency != strategy.base_currency, параметр base_money_position в событии")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C621173() throws Exception {
        String SIEBEL_ID_MASTER = "1-5EEFOQV";
        String SIEBEL_ID_SLAVE = "1-1GBV9PT";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        LocalDateTime dateCreateTr = null;
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        String contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        clientIdSlave = findValidAccountWithSiebleIdSlave.get(0).getInvestAccount().getId();
        List<ClientCode> findClientCodeByContractIdSlave = billingService.getFindClientCodeByBrokerAccountId(contractIdSlave);
        String clientCodeSlave = findClientCodeByContractIdSlave.get(0).getId();
        //создаем команду для топика tracking.event, чтобы очистился кеш contractCache
        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String key = null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по базовой валюте
            clientMiofApi.clientAdjustCurrencyGet()
                .typeQuery("Withdraw")
                .amountQuery(1).currencyQuery("RUB")
                .clientCodeQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
            //ловим команду, в топике kafka tracking.master.command
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
            //парсим команду: получаем key, value, достаем значение created_at из value
            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                    portfolioCommand = portfolioCommandBefore;
                    key = records.get(i).key;
                    break;
                }
            }
        }
        //приводим значение created_at к нужному формату до минут
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //вызываем метод middleOffice, который возвращает список позиций клиента
        InlineResponse20014 expecResponsePos =
            clientMiofApi.clientPositionsGet()
                .agrNumQuery(contractIdSlave)
                .clientIdQuery(clientCodeSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(InlineResponse20014.class));

        //находим значение balance и locked по бумаге FXIT
        double balanceCur = 0.0;
        double lockedCur = 0.0;
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCur = simpleMoneyPosition.getBalance().getValue();
                lockedCur = simpleMoneyPosition.getBlocked().getValue();
            }
        }
        double balanceCurBase = 0.0;
        double lockedCurBase = 0.0;
        //находим balance и locked по базовой валюте
        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
            if ("365".equals(simpleMoneyPosition.getKind())
                && "USD".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
                balanceCurBase = simpleMoneyPosition.getBalance().getValue();
                lockedCurBase = simpleMoneyPosition.getBlocked().getValue();
            }
        }
        double quantityMiddleCur = balanceCur + lockedCur;
        double quantityMiddleCurBase = balanceCurBase + lockedCurBase;
        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
        String ticker = null;
        String tradingClearingAccount = null;
        String action = null;
        double unscaled = 0.0;
        double scale = 0.0;
        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
            if ("RUB".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
                break;
            }
        }
        double quantityTrackingCur = unscaled * Math.pow(10, -1 * scale);
        double quantityTrackingCurBase = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("ticker не равен", ticker, is("RUB"));
        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("MB9885503216"));
        assertThat("action  не равен", action, is("ADJUST_CURRENCY"));
        assertThat("quantity по валюте  не равен", (quantityTrackingCur), is(quantityMiddleCur));
        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCurBase), is(quantityMiddleCurBase));
        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
    }


    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(String SIEBEL_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
            .setActivationTime(date);
        strategy = trackingService.saveStrategy(strategy);
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

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) throws InterruptedException {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        String key = contractIdSlave;
        //отправляем событие в топик kafka tracking.event
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.event");
        template.sendDefault(key, eventBytes);
        template.flush();
        Thread.sleep(10000);
    }
}
