package stpTrackingMaster.handleActualizeCommand;

import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
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
import stpTrackingRetryer.handle30DelayRetryCommand.Handle30DelayRetryCommandTest;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_30_DELAY_RETRYER_COMMAND;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию виртуального портфеля")
@Feature("TAP-8055")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-master")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaProtobufFactoryAutoConfiguration.class})
public class HandleActualizeCommandErrorTest {
    KafkaHelper kafkaHelper = new KafkaHelper();

    @Resource(name = "customSenderFactory")
    KafkaProtobufCustomSender<String, byte[]> kafkaSender;

    @Resource(name = "bytesReceiverFactory")
    KafkaProtobufBytesReceiver<String, BytesValue> receiverBytes;

    @Resource(name = "customReceiverFactory")
    KafkaProtobufCustomReceiver<String, byte[]> kafkaReceiver;

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
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    MasterSignalDao masterSignalDao;

    ExchangePositionApi exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    MasterPortfolio masterPortfolio;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String contractIdMaster;
    UUID strategyIdMaster;
    int version;
    String ticker = "XS0587031096";
    String tradingClearingAccount = "L01+00000SPB";

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
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyIdMaster);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignal(strategyIdMaster, version);
            } catch (Exception e) {
            }
        });
    }

    @SneakyThrows
    @Test
    @AllureId("662316")
    @DisplayName("C662316.HandleActualizeCommand.Стратегия не найдена по contractId")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662316() {
        String siebelIdMaster = "1-DPVDVIC";
        strategyIdMaster = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, 4, 12, 0,
            49900, 1, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send("tracking.master.command", key, eventBytes);
        Optional<MasterPortfolio> portfolio = masterPortfolioDao.findLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("662385")
    @DisplayName("C662385.HandleActualizeCommand.Не найден текущий портфель master'а в бд Cassandra по contract_id + strategy")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662385() {
        String siebelIdMaster = "1-DPVDVIC";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        strategyIdMaster = strategyId;
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, 4, 12, 0,
            49900, 1, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send("tracking.master.command", key, eventBytes);
        Optional<MasterPortfolio> portfolio = masterPortfolioDao.findLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("662386")
    @DisplayName("C662386.HandleActualizeCommand.Version из команды < master_portfolio.version")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662386() {
        String siebelIdMaster = "1-DPVDVIC";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        int versionPortfolio = 3;
        int versionCommand = 2;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        strategyIdMaster = strategyId;
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(versionPortfolio, "5000.0", "5.0", date);

        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, versionCommand, 12,
            0, 49900, 1, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send("tracking.master.command", key, eventBytes);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(3));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("5.0"));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5000.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("662387")
    @DisplayName("C662387.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля > 1")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662387() {
        String siebelIdMaster = "1-DPVDVIC";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        int versionPortfolio = 3;
        int versionCommand = 5;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        strategyIdMaster = strategyId;
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(versionPortfolio, "5000.0", "5.0", date);
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, versionCommand, 12,
            0, 49900, 1, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractIdMaster;
        //отправляем событие в топик kafka social.event
        kafkaSender.send("tracking.master.command", key, eventBytes);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(versionPortfolio));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("5.0"));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5000.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    @SneakyThrows
    @Test
    @AllureId("719222")
    @DisplayName("C719222.HandleActualizeCommand.Валидация команды на актуализацию. В массиве portfolio.position содержится только один элемент")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C719222() {
        String siebelIdMaster = "1-51Q76AT";
        String siebelIdSlave = "5-1AC5OMAJ0";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        String key = null;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebleId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        strategyIdMaster = strategyId;
        // создаем портфель ведущего  в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolio(version, "5000.0", "5.0", date);
        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем все события из tracking.slave.command
        resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //формируем команду на актуализацию по ведущему
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommandValid(contractIdMaster, now, version,
            10, 0, 49900, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send("tracking.master.command", key, eventBytes);
        //смотрим, сообщение, топике kafka его быть не должно
        Optional<Map<String, byte[]>> optional = await().atMost(Duration.ofSeconds(20))
            .until(
                () -> kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND.getName()),
                is(empty())
            ).stream().findFirst();
    }


    @SneakyThrows
    @Test
    @AllureId("720454")
    @DisplayName("C720454.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        "Найдена запись в master_signal, данные найденного сигнала в master_signal не совпадают с данными команды")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C720454() {
        String siebelIdMaster = "1-51Q76AT";
        String siebelIdSlave = "5-13U2T8ND8";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebleId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        strategyIdMaster = strategyId;
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String tickerPos = "MTS0620";
        String tradingClearingAccountPos = "L01+00000SPB";
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(tickerPos, tradingClearingAccountPos, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);

        //создаем запись о сигнале
        Byte action = (byte) 11;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyIdMaster, version, action, date, price, quantity, null, "AAPL", "L01+00000SPB");

        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему

        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.master.command");
        template.sendDefault(keyMaster, eventBytes);
        template.flush();
        //включаем kafkaConsumer и слушаем топик tracking.slave.command
        List<KafkaMessageConsumer.Record<String, byte[]>> records = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);
            messageConsumer.startUp();
            Thread.sleep(5000);
            messageConsumer.await()
                .ifPresent(r -> {
                    throw new RuntimeException("Команда получена");
                });
        }
    }


    @SneakyThrows
    @Test
    @AllureId("721195")
    @DisplayName("C721195.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        " master_signal.state заполнен")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C721195() {
        String siebelIdMaster = "1-51Q76AT";
        String siebelIdSlave = "5-13U2T8ND8";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebleId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();

        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebleId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());

        strategyIdMaster = strategyId;
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String tickerPos = "MTS0620";
        String tradingClearingAccountPos = "L01+00000SPB";
        String quantityPos = "1";
        int versionPos = version - 1;
        int versionPortfolio = version - 1;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(tickerPos, tradingClearingAccountPos, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);

        //создаем запись о сигнале
        Byte action = (byte) 12;
        Byte state = (byte) 1;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyIdMaster, version, action, date, price, quantity, state, ticker, tradingClearingAccount);

        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему

        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.master.command");
        template.sendDefault(keyMaster, eventBytes);
        template.flush();
        //включаем kafkaConsumer и слушаем топик tracking.slave.command
        List<KafkaMessageConsumer.Record<String, byte[]>> records = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.slave.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);
            messageConsumer.startUp();
            Thread.sleep(5000);
            messageConsumer.await()
                .ifPresent(r -> {
                    throw new RuntimeException("Команда получена");
                });
        }
    }


/////////***методы для работы тестов**************************************************************************

    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, int version,
                                                                            long unscaled, int scale, long unscaledBaseMoney,
                                                                            int scaleBaseMoney, Tracking.Decimal priceS,
                                                                            Tracking.Decimal quantityS) {
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
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
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
            .setPrice(priceS)
            .setQuantity(quantityS)
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

    void createMasterPortfolio(int version, String money, String quantityPos, Date date) {
        //создаем портфель master в cassandra
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .build());
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyIdMaster, version, baseMoneyPosition, positionList);
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


    //метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
    void getExchangePosition(String ticker, String tradingClearingAccount, ExchangePosition.ExchangeEnum exchange,
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
            exchangePositionApi.createExchangePosition()
                .reqSpec(r -> r.addHeader("api-key", "tracking"))
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

    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandValid(String contractId, OffsetDateTime time, int version,
                                                                                 long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                                 Tracking.Portfolio.Action action) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaledBaseMoney)
            .setScale(scaleBaseMoney)
            .build();

        Tracking.Portfolio.Position positionOne = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(action).build())
            .setQuantity(quantity)
            .build();
        Tracking.Portfolio.Position positionTwo = Tracking.Portfolio.Position.newBuilder()
            .setTicker("AAPL")
            .setTradingClearingAccount("L01+00000SPB")
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
            .addPosition(positionOne)
            .addPosition(positionTwo)
            .setBaseMoneyPosition(baseMoneyPosition)
            .build();
        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .build();
        return command;
    }

    //создаем портфель master в cassandra с позицией
    void createMasterPortfolioWithPosition(String ticker, String tradingClearingAccount, String quantityPos,
                                           Tracking.Portfolio.Position position,
                                           int versionPos, int version, String money, Date date) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .lastChangeDetectedVersion(versionPos)
            .changedAt(date)
            .quantity(new BigDecimal(quantityPos))
            .build());
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyIdMaster, version, baseMoneyPosition, positionList);
    }

    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandNoBaseMoney(String contractId, OffsetDateTime time, int version,
                                                                                       long unscaled, int scale) {
        Tracking.Decimal quantity = Tracking.Decimal.newBuilder()
            .setUnscaled(unscaled)
            .setScale(scale)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .setQuantity(quantity)
            .build();
        Tracking.PortfolioCommand command;
        Tracking.Portfolio portfolio = Tracking.Portfolio.newBuilder()
            .setVersion(version)
            .addPosition(position)
            .build();
        command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(portfolio)
            .build();
        return command;
    }

    //создаем запись в master_signal
    void createMasterSignal(UUID strategyIdMaster, int version, Byte action, Date createAt, BigDecimal price, BigDecimal quantity,
                            Byte state, String ticker, String tradingClearingAccount) {
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyIdMaster)
            .version(version)
            .action(action)
            .createdAt(createAt)
            .price(price)
            .quantity(quantity)
            .state(state)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .build();
        //insert запись в cassandra master_signal
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }

    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommand(String contractId, OffsetDateTime time, int version,
                                                                            long unscaled, int scale, long unscaledBaseMoney, int scaleBaseMoney,
                                                                            Tracking.Portfolio.Action action, Tracking.Decimal price,
                                                                            Tracking.Decimal quantityS) {
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


    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Полечен запрос на вычитавание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> receiverBytes.receiveBatch(topic.getName(),
                Duration.ofSeconds(3), BytesValue.class), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }

}
