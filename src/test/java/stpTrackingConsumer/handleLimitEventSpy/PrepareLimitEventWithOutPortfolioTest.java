package stpTrackingConsumer.handleLimitEventSpy;


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
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.entities.ClientCode;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
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
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
public class PrepareLimitEventWithOutPortfolioTest {

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
    StrategyService strategyService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    ProfileService profileService;

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
            } catch (Exception e) {}
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {}
            try {
                clientSlave= clientService.getClient(clientSlave.getId());
            } catch (Exception e) {}
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {}
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {}
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {}
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {}
//            try {
//                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
//            } catch (Exception e) {}
        });
    }

    String contractIdSlave;
    UUID strategyId;
    UUID clientIdSlave;


    @SneakyThrows
    @Test
    @AllureId("617069")
    @DisplayName("C617069.PrepareLimitEvent.Проверка формировая события limitEvent")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
    void C641029() throws  Exception {
        String SIEBEL_ID_MASTER = "1-7UOOABF";
        String SIEBEL_ID_SLAVE = "5-59ELX2E3";
        String title = "тест стратегия autotest update adjust security";
        String description = "description test стратегия autotest update adjust security";
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
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        String key= null;
        //включаем kafka - consumer для топика tracking.slave.command
        Tracking.PortfolioCommand portfolioCommand = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.startUp();
            //вызываем метод middleOffice по изменению позиции клиента по ценной бумаге
            RuTinkoffTradingMiddlePositionsPositionsResponse expecResponse =
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
            //парсим команду: получаем key, value, достаем значение created_at из value
//            KafkaMessageConsumer.Record<String, byte[]> record = records.get(records.size() - 1);

            for (int i = 0; i < records.size(); i++) {
                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
                if ((contractIdSlave.equals(portfolioCommandBefore.getContractId()))
                    &("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString())))
                {
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
        //находим значение balance и locked по бумаге FXIT
        double balanceSer = 0.0;
        double lockedSer = 0.0;
        for (RuTinkoffTradingMiddlePositionsSimpleSecurityPosition simpleSecuritiesPosition :  expecResponsePos.getPayload().getSecurities()) {
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
        double  quantityMiddleSer = balanceSer + lockedSer;
        double  quantityMiddleCur = balanceCur + lockedCur;

        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
        String ticker = null;
        String tradingClearingAccount = null;
        String action = null;
        double unscaled = 0.0;
        double scale =  0.0;
        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
            if ("FXIT".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
            }
        }
        double quantityTrackingSer =  unscaled * Math.pow(10, -1* scale);
        double quantityTrackingCur =  portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1*portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        //проверяем, данные в команде
        assertThat("key не равен", key, is(contractIdSlave));
        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
        assertThat("ticker не равен", ticker, is("FXIT"));
        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("Y02+00001F00"));
        assertThat("action  не равен", action, is("UNKNOWN"));
        assertThat("quantity по бумагам  не равен", (quantityTrackingSer), is(quantityMiddleSer));
        assertThat("quantity BaseMoneyPosition не равен",(quantityTrackingCur), is(quantityMiddleCur));
    }





//
//    @Test
//    @AllureId("621630")
//    @DisplayName("C621630.HandleLimitEvent.Формирование команды об изменении позиции для master")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
//    void C621630() throws Exception {
//        String SIEBEL_ID = "5-5I0U53EV";
//        //получаем текущую дату и время
//        OffsetDateTime now = OffsetDateTime.now();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
//        String dateNow = (fmt.format(now));
//        //получаем данные по клиенту в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        contractId = findValidAccountWithSiebleId.get(0).getId();
//        List<ClientCode> findClientCodeByContractId = billingService.getFindClientCodeByBrokerAccountId(contractId);
//        String clientCode = findClientCodeByContractId.get(0).getId();
//        //создаем в БД tracking  договор: client, contract, strategy
//        strategyId = UUID.randomUUID();
//        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId, ContractRole.master, ContractState.tracked,
//            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active);
//        //создаем портфель master в cassandra
//        List<MasterPortfolio.Position> positionList = new ArrayList<>();
//        positionList.add(MasterPortfolio.Position.builder()
//            .ticker("RUB")
//            .tradingClearingAccount("MB9885503216")
//            .build());
//        masterPortfolioDao.insertIntoMasterPortfolio(contractId, strategyId, 1, positionList);
//        //включаем kafka - consumer для топика tracking.master.tracking.master.command
//        String key = null;
//        Tracking.PortfolioCommand portfolioCommand = null;
//        LocalDateTime dateCreateTr = null;
//        Thread.sleep(5000);
//        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
//                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
//                     StringDeserializer.class, ByteArrayDeserializer.class)) {
//            messageConsumer.startUp();
//            //вызываем метод middleOffice по изменению позиции клиента по ценной бумаге
//            clientMiofApi.clientAdjustSecurityGet()
//                .quantityQuery(1)
//                .tickerQuery("FXIT")
//                .clientCodeQuery(clientCode)
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
//            //ловим команду, в топике kafka tracking.master.command
//            messageConsumer.setTimeout(7000);
//            Thread.sleep(5000);
//            messageConsumer.await()
//                .orElseThrow(() -> new RuntimeException("Команда не получена"));
//
//            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
//            //парсим команду: получаем key, value, достаем значение created_at из value
////            KafkaMessageConsumer.Record<String, byte[]> record = records.get(records.size() - 1);
//            log.info("records:  {}", records);
//            for (KafkaMessageConsumer.Record<String, byte[]> record : records) {
//                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(record.value);
//                if ((contractId.equals(portfolioCommandBefore.getContractId()))
//                    & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
//                    portfolioCommand = portfolioCommandBefore;
//                    key = record.key;
//                    break;
//                }
//            }
////            key = record.key;
////            portfolioCommand = Tracking.PortfolioCommand.parseFrom(record.value);
////            dateCreateTr = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos())
////                .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
//        }
//        log.info("Команда в tracking.master.command:  {}", portfolioCommand);
//        //приводим значение created_at к нужному формату до минут
//        //TODO: не используем данную проверку пока не поправят в задаче MIOF-632 время отправки событий
////        OffsetDateTime dateFromCommand = OffsetDateTime.of(dateCreateTr, ZoneOffset.of("+3"));
////        String dateFromCommandWithMinut = (fmt.format(dateFromCommand));
//        //вызываем метод middleOffice, который возвращает список позиций клиента
//        InlineResponse20014 expecResponsePos =
//            clientMiofApi.clientPositionsGet()
//                .agrNumQuery(contractId)
//                .clientIdQuery(clientCode)
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(InlineResponse20014.class));
//        //находим значение balance и locked по бумаге FXIT
//        double balanceSer = 0.0;
//        double lockedSer = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleSecurityPosition simpleSecuritiesPosition : expecResponsePos.getPayload().getSecurities()) {
//            if ("365".equals(simpleSecuritiesPosition.getKind())
//                && "FXIT".equals(simpleSecuritiesPosition.getTicker())) {
//                balanceSer = simpleSecuritiesPosition.getBalance();
//                lockedSer = simpleSecuritiesPosition.getBlocked();
//            }
//        }
//        //находим balance и locked по базовой валюте
//        double balanceCur = 0.0;
//        double lockedCur = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
//            if ("365".equals(simpleMoneyPosition.getKind())
//                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
//                balanceCur = simpleMoneyPosition.getBalance().getValue();
//                lockedCur = simpleMoneyPosition.getBlocked().getValue();
//            }
//        }
//        //считаем значение quantity, по позициям, полученное из middle
//        double quantityMiddleSer = balanceSer + lockedSer;
//        double quantityMiddleCur = balanceCur + lockedCur;
//        //находим в команде portfolioCommand позицию, по которой делали изменения в middle
//        String ticker = null;
//        String tradingClearingAccount = null;
//        String action = null;
//        double unscaled = 0.0;
//        double scale = 0.0;
//        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
//            if ("FXIT".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
//                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
//                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
//                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
//                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
//                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
//                break;
//            }
//        }
//        //рассчитываем значение quantity, полученное из команды в tracking.master.command
//        double quantityTrackingSer = unscaled * Math.pow(10, -1 * scale);
//        double quantityTrackingCur = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
//        //проверяем, данные в команде
//        assertThat("key не равен", key, is(contractId));
//        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
//        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
////        assertThat("дата команды по актуализации мастера не равен", dateFromCommandWithMinut, is(dateNow));
//        assertThat("ticker не равен", ticker, is("FXIT"));
//        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("Y02+00001F00"));
//        assertThat("action  не равен", action, is("UNKNOWN"));
//        assertThat("quantity по бумагам  не равен", (quantityTrackingSer), is(quantityMiddleSer));
//        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCur), is(quantityMiddleCur));
//    }
//
//
//
//    @Test
//    @AllureId("620949")
//    @DisplayName("C620949.HandleLimitEvent.Найдена позиция по базовой валюте, параметр base_money_position в событии")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
//    void C620949() throws Exception {
//        String SIEBEL_ID = "5-8XPX4H37";
//        //получаем текущую дату и время
//        OffsetDateTime now = OffsetDateTime.now();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
//        String dateNow = (fmt.format(now));
//        //получаем данные по клиенту в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        contractId = findValidAccountWithSiebleId.get(0).getId();
//        List<ClientCode> findClientCodeByContractId = billingService.getFindClientCodeByBrokerAccountId(contractId);
//        String clientCode = findClientCodeByContractId.get(0).getId();
//        //создаем в БД tracking  договор: client, contract, strategy
//        strategyId = UUID.randomUUID();
//        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId, ContractRole.master, ContractState.tracked,
//            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active);
//        //создаем портфель master в cassandra
//        List<MasterPortfolio.Position> positionList = new ArrayList<>();
//        positionList.add(MasterPortfolio.Position.builder()
//            .ticker("RUB")
//            .tradingClearingAccount("MB9885503216")
//            .build());
//        masterPortfolioDao.insertIntoMasterPortfolio(contractId, strategyId, 1, positionList);
//        //включаем kafka - consumer для топика tracking.master.tracking.master.command
//        Tracking.PortfolioCommand portfolioCommand = null;
//        LocalDateTime dateCreateTr = null;
//        String key=null;
//        Thread.sleep(5000);
//        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
//                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
//                     StringDeserializer.class, ByteArrayDeserializer.class)) {
//
//            messageConsumer.startUp();
//            //вызываем метод middleOffice по изменению позиции клиента по базовой валюте
//                clientMiofApi.clientAdjustCurrencyGet()
//                    .typeQuery("Withdraw")
//                    .amountQuery(1).currencyQuery("RUB")
//                    .clientCodeQuery(clientCode)
//                    .respSpec(spec -> spec.expectStatusCode(200))
//                    .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
//            //ловим команду, в топике kafka tracking.master.command
//            messageConsumer.setTimeout(5000);
//            messageConsumer.await()
//                .orElseThrow(() -> new RuntimeException("Команда не получена"));
//
//            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
//            //парсим команду: получаем key, value, достаем значение created_at из value
//            for (int i = 0; i < records.size(); i++) {
//                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
//                if ((contractId.equals(portfolioCommandBefore.getContractId()))
//                    &("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString())))
//                {
//                    portfolioCommand = portfolioCommandBefore;
//                    key = records.get(i).key;
//                    break;
//                }
//            }
//        }
//        log.info("Команда в tracking.master.command:  {}", portfolioCommand);
//        //вызываем метод middleOffice, который возвращает список позиций клиента
//        InlineResponse20014 expecResponsePos =
//            clientMiofApi.clientPositionsGet()
//                .agrNumQuery(contractId)
//                .clientIdQuery(clientCode)
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(InlineResponse20014.class));
//        //находим balance и locked по базовой валюте
//        double balanceCur = 0.0;
//        double lockedCur = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
//            if ("365".equals(simpleMoneyPosition.getKind())
//                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
//                balanceCur = simpleMoneyPosition.getBalance().getValue();
//                lockedCur = simpleMoneyPosition.getBlocked().getValue();
//            }
//        }
//        //считаем quantity по тем данным, что вернул middle
//        double quantityMiddleCur = balanceCur + lockedCur;
//        // считаем quantity, по тем данным, что получили в команде
//        double quantityTrackingCur = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
//        //проверяем, данные в команде
//        assertThat("key не равен", key, is(contractId));
//        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
//        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(true));
//        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCur), is(quantityMiddleCur));
//    }
//
//
//
//    @Test
//    @AllureId("621173")
//    @DisplayName("C621173.HandleLimitEvent.money_limit.currency != strategy.base_currency, параметр base_money_position в событии")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании.")
//    void C621173() throws Exception {
//        String SIEBEL_ID = "1-1N879M8";
//        //получаем текущую дату и время
//        OffsetDateTime now = OffsetDateTime.now();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
//        String dateNow = (fmt.format(now));
//        //получаем данные по клиенту в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        contractId = findValidAccountWithSiebleId.get(0).getId();
//        List<ClientCode> findClientCodeByContractId = billingService.getFindClientCodeByBrokerAccountId(contractId);
//        String clientCode = findClientCodeByContractId.get(0).getId();
//        //создаем в БД tracking  договор: client, contract, strategy
//        strategyId = UUID.randomUUID();
//        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId, ContractRole.master, ContractState.tracked,
//            StrategyCurrency.usd, StrategyRiskProfile.conservative, StrategyStatus.active);
//        //включаем kafka - consumer для топика tracking.master.tracking.master.command
//        Tracking.PortfolioCommand portfolioCommand = null;
//        LocalDateTime dateCreateTr = null;
//        String key= null;
//
//        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
//                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
//                     StringDeserializer.class, ByteArrayDeserializer.class)) {
//            messageConsumer.startUp();
//            //вызываем метод middleOffice по изменению позиции клиента по валюте
//                clientMiofApi.clientAdjustCurrencyGet()
//                    .typeQuery("Withdraw")
//                    .amountQuery(1).currencyQuery("RUB")
//                    .clientCodeQuery(clientCode)
//                    .respSpec(spec -> spec.expectStatusCode(200))
//                    .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
//            //ловим команду, в топике kafka tracking.master.command
//            Thread.sleep(5000);
//            messageConsumer.setTimeout(5000);
//            messageConsumer.await()
//                .orElseThrow(() -> new RuntimeException("Команда не получена"));
//            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
//            //парсим команду: получаем key, value, достаем значение created_at из value
//            for (int i = 0; i < records.size(); i++) {
//                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
//                if ((contractId.equals(portfolioCommandBefore.getContractId()))
//                    &("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString())))
//                {
//                    portfolioCommand = portfolioCommandBefore;
//                    key = records.get(i).key;
//                    break;
//                }
//            }
//        }
//        log.info("Команда в tracking.master.command:  {}", portfolioCommand);
//        //вызываем метод middleOffice, который возвращает список позиций клиента
//        InlineResponse20014 expecResponsePos =
//            clientMiofApi.clientPositionsGet()
//                .agrNumQuery(contractId)
//                .clientIdQuery(clientCode)
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(InlineResponse20014.class));
//        //находим balance и locked по валюте
//        double balanceCur = 0.0;
//        double lockedCur = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
//            if ("365".equals(simpleMoneyPosition.getKind())
//                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
//                balanceCur = simpleMoneyPosition.getBalance().getValue();
//                lockedCur = simpleMoneyPosition.getBlocked().getValue();
//            }
//        }
//        //считаем quantity по тем данным, что вернул middle по валюте
//        double quantityMiddleCur = balanceCur + lockedCur;
//        //находим balance и locked по базовой валюте
//        double balanceCurBase = 0.0;
//        double lockedCurBase = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
//            if ("365".equals(simpleMoneyPosition.getKind())
//                && "USD".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
//                balanceCurBase = simpleMoneyPosition.getBalance().getValue();
//                lockedCurBase = simpleMoneyPosition.getBlocked().getValue();
//            }
//        }
//        //считаем quantity по тем данным, что вернул middle
//        double quantityMiddleCurBase = balanceCurBase + lockedCurBase;
//        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
//        String ticker = null;
//        String tradingClearingAccount = null;
//        String action = null;
//        double unscaled = 0.0;
//        double scale = 0.0;
//        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
//            if ("RUB".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
//                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
//                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
//                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
//                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
//                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
//            }
//        }
//        double quantityTrackingCur = unscaled * Math.pow(10, -1 * scale);
//        double quantityTrackingCurBase = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
//        //проверяем, данные в команде
//        assertThat("key не равен", key, is(contractId));
//        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
//        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
////        assertThat("дата команды по актуализации мастера не равен", dateFromCommandWithMinut, is(dateNow));
////        assertThat("версия  не равен", portfolioCommand.getPortfolio().getVersion(), is(quantityTrackingSer.intValue()));
//        assertThat("ticker не равен", ticker, is("RUB"));
//        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("MB9885503216"));
//        assertThat("action  не равен", action, is("UNKNOWN"));
//        assertThat("quantity по валюте  не равен", (quantityTrackingCur), is(quantityMiddleCur));
//        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCurBase), is(quantityMiddleCurBase));
//    }
//
//
//    @Test
//    @AllureId("580016")
//    @DisplayName("C580016.HandleLimitEvent.Позиция по базовой валюте - нулевая, параметр base_money_position в событии")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки параметра base_money_position")
//    void C621176() throws Exception {
//        String SIEBEL_ID = "4-IP4OM7T";
//        //получаем текущую дату и время
//        OffsetDateTime now = OffsetDateTime.now();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
//        String dateNow = (fmt.format(now));
//        //получаем данные по клиенту в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        contractId = findValidAccountWithSiebleId.get(0).getId();
//        List<ClientCode> findClientCodeByContractId = billingService.getFindClientCodeByBrokerAccountId(contractId);
//        String clientCode = findClientCodeByContractId.get(0).getId();
//        //создаем в БД tracking  договор: client, contract, strategy
//        strategyId = UUID.randomUUID();
//        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId, ContractRole.master, ContractState.tracked,
//            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active);
//        //создаем портфель master в cassandra
//        List<MasterPortfolio.Position> positionList = new ArrayList<>();
//        positionList.add(MasterPortfolio.Position.builder()
//            .ticker("RUB")
//            .tradingClearingAccount("MB9885503216")
//            .build());
//        masterPortfolioDao.insertIntoMasterPortfolio(contractId, strategyId, 1, positionList);
//        //включаем kafka - consumer для топика tracking.master.tracking.master.command
//        Tracking.PortfolioCommand portfolioCommand = null;
//        LocalDateTime dateCreateTr = null;
//        String key = null;
//
//        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
//                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
//                     StringDeserializer.class, ByteArrayDeserializer.class)) {
//            messageConsumer.startUp();
//            //вызываем метод middleOffice по изменению позиции клиента по базовой валюте
//            RuTinkoffTradingMiddlePositionsPositionsResponse expecResponse =
//                clientMiofApi.clientAdjustCurrencyGet()
//                    .typeQuery("Withdraw")
//                    .amountQuery(1).currencyQuery("USD")
//                    .clientCodeQuery(clientCode)
//                    .respSpec(spec -> spec.expectStatusCode(200))
//                    .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
//            //ловим команду, в топике kafka tracking.master.command
//            Thread.sleep(5000);
//            messageConsumer.setTimeout(5000);
//            messageConsumer.await()
//                .orElseThrow(() -> new RuntimeException("Команда не получена"));
//            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
//            //парсим команду: получаем key, value, достаем значение created_at из value
//            for (int i = 0; i < records.size(); i++) {
//                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
//                if ((contractId.equals(portfolioCommandBefore.getContractId()))
//                    &("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString())))
//                {
//                    portfolioCommand = portfolioCommandBefore;
//                    key = records.get(i).key;
//                    break;
//                }
//            }
//        }
//        log.info("Команда в tracking.master.command:  {}", portfolioCommand);
//        //вызываем метод middleOffice, который возвращает список позиций клиента
//        InlineResponse20014 expecResponsePos =
//            clientMiofApi.clientPositionsGet()
//                .agrNumQuery(contractId)
//                .clientIdQuery(clientCode)
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(InlineResponse20014.class));
//        //находим balance и locked по валюте
//        double balanceCur = 0.0;
//        double lockedCur = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
//            if ("365".equals(simpleMoneyPosition.getKind())
//                && "USD".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
//                balanceCur = simpleMoneyPosition.getBalance().getValue();
//                lockedCur = simpleMoneyPosition.getBlocked().getValue();
//                break;
//            }
//        }
//
//        //считаем quantity по тем данным, что вернул middle по валюте
//        double quantityMiddleCur = balanceCur + lockedCur;
//
//        //находим balance и locked по базовой валюте
//        double balanceCurBase = 0.0;
//        double lockedCurBase = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleMoneyPosition simpleMoneyPosition : expecResponsePos.getPayload().getMoney()) {
//            if ("365".equals(simpleMoneyPosition.getKind())
//                && "RUB".equals(simpleMoneyPosition.getBalance().getCurrency().getName())) {
//                balanceCurBase = simpleMoneyPosition.getBalance().getValue();
//                lockedCurBase = simpleMoneyPosition.getBlocked().getValue();
//                break;
//            }
//        }
//        //считаем quantity по тем данным, что вернул middle
//        double quantityMiddleCurBase = balanceCurBase + lockedCurBase;
//
//        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
//        boolean found = false;
//        String ticker = null;
//        String tradingClearingAccount = null;
//        String action = null;
//        double unscaled = 0.0;
//        double scale = 0.0;
//        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
//            if ("USD000UTSTOM".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
//                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
//                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
//                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
//                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
//                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
//            }
//            if ("RUB".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
//                found = true;
//            }
//
//        }
//        double quantityTrackingCur = unscaled * Math.pow(10, -1 * scale);
//        double quantityTrackingCurBase = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
//        //проверяем, данные в команде
//        assertThat("key не равен", key, is(contractId));
//        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
//        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("ticker не равен", ticker, is("USD000UTSTOM"));
//        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("MB9885503216"));
//        assertThat("action  не равен", action, is("UNKNOWN"));
//        assertThat("quantity по валюте  не равен", (quantityTrackingCur), is(quantityMiddleCur));
//        assertThat("quantity BaseMoneyPosition не равен", (quantityTrackingCurBase), is(quantityMiddleCurBase));
//        assertThat("позиция по базовой валюте не равна", (found), is(false));
//    }
//
//
//    @Test
//    @AllureId("621220")
//    @DisplayName("C621220.HandleLimitEvent.Отсутствует позиция по базовой валюте, параметр base_money_position в событии")
//    @Subfeature("Успешные сценарии")
//    @Description("Операция для обработки параметра base_money_position")
//    void C621220() throws Exception {
//        String SIEBEL_ID = "5-F0U99AGZ";
//        //получаем текущую дату и время
//        OffsetDateTime now = OffsetDateTime.now();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
//        String dateNow = (fmt.format(now));
//        //получаем данные по клиенту в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        contractId = findValidAccountWithSiebleId.get(0).getId();
//        List<ClientCode> findClientCodeByContractId = billingService.getFindClientCodeByBrokerAccountId(contractId);
//        String clientCode = findClientCodeByContractId.get(0).getId();
//        //создаем в БД tracking  договор: client, contract, strategy
//        strategyId = UUID.randomUUID();
//        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId, ContractRole.master, ContractState.tracked,
//            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active);
//        Tracking.PortfolioCommand portfolioCommand = null;
//        LocalDateTime dateCreateTr = null;
//        String key = null;
//
//        //включаем kafka - consumer для топика tracking.master.tracking.master.command
//        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
//                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
//                     StringDeserializer.class, ByteArrayDeserializer.class)) {
//            messageConsumer.startUp();
//            //вызываем метод middleOffice по изменению позиции клиента по ценной бумаге
//            RuTinkoffTradingMiddlePositionsPositionsResponse expecResponse =
//                clientMiofApi.clientAdjustSecurityGet()
//                    .quantityQuery(1)
//                    .tickerQuery("FXIT")
//                    .clientCodeQuery(clientCode)
//                    .respSpec(spec -> spec.expectStatusCode(200))
//                    .execute(response -> response.as(RuTinkoffTradingMiddlePositionsPositionsResponse.class));
//            //ловим команду, в топике kafka tracking.master.command
//            messageConsumer.setTimeout(5000);
//            Thread.sleep(5000);
//            messageConsumer.await()
//                .orElseThrow(() -> new RuntimeException("Команда не получена"));
//            List<KafkaMessageConsumer.Record<String, byte[]>> records = messageConsumer.listRecords();
//            //парсим команду: получаем key, value, достаем значение created_at из value
//            for (int i = 0; i < records.size(); i++) {
//                Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
//                if ((contractId.equals(portfolioCommandBefore.getContractId()))
//                    &("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString())))
//                {
//                    portfolioCommand = portfolioCommandBefore;
//                    key = records.get(i).key;
//                    break;
//                }
//            }
//        }
//        log.info("Команда в tracking.master.command:  {}", portfolioCommand);
//        //вызываем метод middleOffice, который возвращает список позиций клиента
//        InlineResponse20014 expecResponsePos =
//            clientMiofApi.clientPositionsGet()
//                .agrNumQuery(contractId)
//                .clientIdQuery(clientCode)
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(InlineResponse20014.class));
//        //находим значение balance и locked по бумаге FXIT
//        double balanceSer = 0.0;
//        double lockedSer = 0.0;
//        for (RuTinkoffTradingMiddlePositionsSimpleSecurityPosition simpleSecuritiesPosition : expecResponsePos.getPayload().getSecurities()) {
//            if ("365".equals(simpleSecuritiesPosition.getKind())
//                && "FXIT".equals(simpleSecuritiesPosition.getTicker())) {
//                balanceSer = simpleSecuritiesPosition.getBalance();
//                lockedSer = simpleSecuritiesPosition.getBlocked();
//                break;
//            }
//        }
//        double quantityMiddleSer = balanceSer + lockedSer;
//
//        //находим в команде portfolioCommand позицию, по котрой делали изменения в middle
//        String ticker = null;
//        String tradingClearingAccount = null;
//        String action = null;
//        double unscaled = 0.0;
//        double scale = 0.0;
//        for (int i = 0; i < portfolioCommand.getPortfolio().getPositionCount(); i++) {
//            if ("FXIT".equals(portfolioCommand.getPortfolio().getPosition(i).getTicker())) {
//                ticker = portfolioCommand.getPortfolio().getPosition(i).getTicker();
//                tradingClearingAccount = portfolioCommand.getPortfolio().getPosition(i).getTradingClearingAccount();
//                action = portfolioCommand.getPortfolio().getPosition(i).getAction().getAction().toString();
//                unscaled = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getUnscaled();
//                scale = portfolioCommand.getPortfolio().getPosition(i).getQuantity().getScale();
//                break;
//            }
//
//        }
//        double quantityTrackingSer = unscaled * Math.pow(10, -1 * scale);
//        //проверяем, данные в команде
//        assertThat("key не равен", key, is(contractId));
//        assertThat("ID договора не равен", portfolioCommand.getContractId(), is(contractId));
//        assertThat("тип операции не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("ticker не равен", ticker, is("FXIT"));
//        assertThat("trading_clearing_account  не равен", tradingClearingAccount, is("Y02+00001F00"));
//        assertThat("action  не равен", action, is("UNKNOWN"));
//        assertThat("quantity по бумагам  не равен", (quantityTrackingSer), is(quantityMiddleSer));
//        assertThat("BaseMoneyPosition не равен", portfolioCommand.getPortfolio().getBaseMoneyPosition().hasQuantity(), is(false));
//    }
//
//
//    //***методы для работы тестов**************************************************************************
//    //метод создает клиента, договор и стратегию в БД автоследования
//    UUID createClientWithStrategy(String SIEBEL_ID, UUID investId, String contractId) {
//        //создаем клиента в табл. client
//        client = clientService.createClient(investId, ClientStatusType.registered, null);
//        //формируем тело запроса
//        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
//        request.setContractId(contractId);
//        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
//        request.setDescription("autotest стратегия по актуализации портфеля description");
//        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
//        request.setTitle("autotest стратегия по актуализации портфеля");
//        // вызываем метод CreateStrategy
//        Response expectedResponse = strategyApi.createStrategy()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xDeviceIdHeader("new")
//            .xTcsSiebelIdHeader(SIEBEL_ID)
//            .body(request)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response);
//        //достаем из response идентификатор стратегии
//        UUID strategyRest = UUID.fromString(expectedResponse.getBody().asString().substring(19, 55));
//        return strategyRest;
//    }



// //***методы для работы тестов**************************************************************************

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(String SIEBEL_ID, UUID investId,  String contractId, ContractRole contractRole, ContractState contractState,
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
    void createSubscriptionSlave (String siebleIdSlave, String contractIdSlave, UUID strategyId) {
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






//    //метод создает клиента, договор и стратегию в БД автоследования
//    void createClientWithContractAndStrategy (UUID investId, ClientStatusType сlientStatusType, SocialProfile socialProfile, String contractId, UUID strategyId, ContractRole contractRole,
//                                              ContractState contractState, StrategyCurrency strategyCurrency,
//                                              StrategyRiskProfile strategyRiskProfile, StrategyStatus strategyStatus) {
//        client = clientService.createClient(investId, сlientStatusType, socialProfile);
//        contract = new Contract()
//            .setId(contractId)
//            .setClientId(client.getId())
//            .setRole(contractRole)
//            .setState(contractState)
//            .setStrategyId(strategyId)
//            .setBlocked(false);
//
//        contract = contractService.saveContract(contract);
//
//        strategy = new Strategy()
//            .setId(strategyId)
//            .setContract(contract)
//            .setTitle("Тест стратегия автотестов")
//            .setBaseCurrency(strategyCurrency)
//            .setRiskProfile(strategyRiskProfile)
//            .setDescription("Тестовая стратегия для работы автотестов")
//            .setStatus(strategyStatus)
//            .setSlavesCount(0);
//
//        strategy = trackingService.saveStrategy(strategy);
//    }
//

}
