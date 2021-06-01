package stpTrackingMaster.handleActualizeCommand;


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
import ru.qa.tinkoff.tracking.steps.StpTrackingMasterSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию виртуального портфеля")
@Feature("TAP-8055")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-master")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class})
public class HandleActualizeCommandTest {
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
    MasterSignalDao masterSignalDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingMasterSteps steps;

    ExchangePositionApi exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    MasterPortfolio masterPortfolio;
    MasterSignal masterSignal;
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
    @AllureId("662315")
    @DisplayName("C662315.HandleActualizeCommand.Version из команды = master_portfolio.version, strategy.status = 'active'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662315() {
        String siebelIdMaster = "1-51Q76AT";
        String siebelIdSlave = "5-RE91G9E4";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelIdSlave = billingService.getFindValidAccountWithSiebelId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebelIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
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
        int versionPos = version;
        int versionPortfolio = version;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(tickerPos, tradingClearingAccountPos, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем запись о сигнале
        Byte action = (byte) 12;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyIdMaster, version, action, date, price, quantity, null, ticker, tradingClearingAccount);
        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceSignal = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantitySignal = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49900, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE,
            priceSignal, quantitySignal, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractIdSlave.equals(portfolioCommandBefore.getContractId())
                & ("SYNCHRONIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //проверяем параметры команды по синхронизации
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is("SYNCHRONIZE"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
        // проверяем портфель мастера, что он не изменился
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        // проверяем, что обновился state = 1
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyIdMaster, version);
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("726667")
    @DisplayName("C726667.HandleActualizeCommand.Version из команды = master_portfolio.version, strategy.status = 'draft'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C726667() {
        String siebelIdMaster = "1-51Q76AT";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
        strategyIdMaster = strategyId;
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        String tickerPos = "MTS0620";
        String tradingClearingAccountPos = "L01+00000SPB";
        String quantityPos = "1";
        int versionPos = version;
        int versionPortfolio = version;
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(tickerPos, tradingClearingAccountPos, quantityPos, positionAction, versionPos, versionPortfolio,
            baseMoneyPortfolio, date);
        //создаем запись о сигнале
        Byte action = (byte) 12;
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyIdMaster, version, action, date, price, quantity, null, ticker, tradingClearingAccount);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceSignal = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantitySignal = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49900, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE,
            priceSignal, quantitySignal, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
        // проверяем портфель мастера, что он не изменился
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        // проверяем, что обновился state = 1
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyIdMaster, version);
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("662388")
    @DisplayName("C662388.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1.Buy")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C662388() {
        String siebelIdMaster = "1-DC5C5KJ";
        String siebelIdSlave = "5-7T71HO5B";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");

        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelIdSlave = billingService.getFindValidAccountWithSiebelId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebelIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
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
        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal price = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();

        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, price,
            quantityS, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractIdSlave.equals(portfolioCommandBefore.getContractId())
                & ("SYNCHRONIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is("SYNCHRONIZE"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney.toString()));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(tickerPos));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccountPos));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(1).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(1).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(1).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeAction().toString(), is("12"));
    }


    @SneakyThrows
    @Test
    @AllureId("719882")
    @DisplayName("C719882.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        "Не найдена запись в master_signal")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C719882() {
        String siebelIdMaster = "1-DC5C5KJ";
        String siebelIdSlave = "5-14D9VHBMH";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");

        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelIdSlave = billingService.getFindValidAccountWithSiebelId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebelIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
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
        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal price = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();

        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, price,
            quantityS, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractIdSlave.equals(portfolioCommandBefore.getContractId())
                & ("SYNCHRONIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is("SYNCHRONIZE"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney.toString()));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(tickerPos));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccountPos));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(1).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(1).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(1).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeAction().toString(), is("12"));

        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyIdMaster, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("12"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(price.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(ticker));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("720132")
    @DisplayName("C720132.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1.Buy." +
        "Найдена запись в master_signal")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C720132() {
        String siebelIdMaster = "1-DC5C5KJ";
        String siebelIdSlave = "5-14DCU08B1";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");

        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelIdSlave = billingService.getFindValidAccountWithSiebelId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebelIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
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
        BigDecimal price = new BigDecimal("256");
        BigDecimal quantity = new BigDecimal("2");
        createMasterSignal(strategyIdMaster, version, action, date, price, quantity, null, ticker, tradingClearingAccount);

        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, priceS,
            quantityS, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractIdSlave.equals(portfolioCommandBefore.getContractId())
                & ("SYNCHRONIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is("SYNCHRONIZE"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney.toString()));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(tickerPos));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccountPos));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(1).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(1).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(1).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeAction().toString(), is("12"));

        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyIdMaster, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("12"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(priceS.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(ticker));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("663357")
    @DisplayName("C663357.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1, не передан base_money_position")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C663357() {
        String siebelIdMaster = "1-F6L0ULT";
        String siebelIdSlave = "5-14B2PI4GG";
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
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelIdSlave = billingService.getFindValidAccountWithSiebelId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebelIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
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
        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = createActualizeCommandToTrackingMasterCommandNoBaseMoney(contractIdMaster, now, version,
            10, 0, priceS, quantityS);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractIdSlave.equals(portfolioCommandBefore.getContractId())
                & ("SYNCHRONIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        Instant createAt = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is("SYNCHRONIZE"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneyPortfolio));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(tickerPos));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccountPos));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(1).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(1).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(1).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeAction().toString(), is("12"));
    }


    @SneakyThrows
    @Test
    @AllureId("663495")
    @DisplayName("C663495.Version из команды - master_portfolio.version найденного портфеля = 1, strategy.status = 'draft'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C663495() {
        String siebelIdMaster = "1-BABKO0G";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
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
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version,
            10, 0, 49850, 1, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, priceS,
            quantityS, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //отправляем команду в топик kafka tracking.master.command
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.master.command");
        template.sendDefault(keyMaster, eventBytes);
        template.flush();
        Thread.sleep(5000);
        // проверяем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoney.toString()));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(tickerPos));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccountPos));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is(quantityPos));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant(), is(date.toInstant()));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(versionPos));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("12"));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(1).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(1).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(1).getQuantity().toString(), is("10"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(1).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeDetectedVersion(), is(version));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(1).getLastChangeAction().toString(), is("12"));
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyIdMaster, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("12"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(date.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(priceS.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(ticker));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    @SneakyThrows
    @Test
    @AllureId("666901")
    @DisplayName("C666901.HandleActualizeCommand.Version из команды - master_portfolio.version найденного портфеля = 1.Sell")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C666901() {
        String siebelIdMaster = "1-1VAEYWG";
        String siebelIdSlave = "5-5Y3SGW2V";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        version = 2;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(siebelIdMaster);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //находим данные по ведомому в Бд сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebelIdSlave = billingService.getFindValidAccountWithSiebelId(siebelIdSlave);
        String contractIdSlave = findValidAccountWithSiebelIdSlave.get(0).getId();
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        strategyIdMaster = strategyId;
        // создаем   портфель ведущего  в кассандре c позицией
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        String quantityPos = "1";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(ticker, tradingClearingAccount, quantityPos, positionAction, version, version,
            baseMoneyPortfolio, date);
        //создаем подписку на стратегию
        createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyIdMaster);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем команду на актуализацию по ведущему
        Tracking.Decimal priceS = Tracking.Decimal.newBuilder()
            .setUnscaled(256).build();
        Tracking.Decimal quantityS = Tracking.Decimal.newBuilder()
            .setUnscaled(2).build();
        Tracking.PortfolioCommand command = steps.createActualizeCommandToTrackingMasterCommand(contractIdMaster, now, version + 1, 0, 0,
            50000, 1, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, priceS, quantityS, ticker, tradingClearingAccount);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
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
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractIdSlave.equals(portfolioCommandBefore.getContractId())
                & ("SYNCHRONIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        dateCreateTr = Instant.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos())
            .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        //приводим значение created_at к нужному формату до минут
        OffsetDateTime dateFromCommand = OffsetDateTime.of(dateCreateTr, ZoneOffset.of("+3"));
        String dateFromCommandWithMinut = (fmt.format(dateFromCommand));
        //проверяем параметры команды по синхронизации
        assertThat("ID договора ведомого не равен", portfolioCommand.getContractId(), is(contractIdSlave));
        assertThat("operation команды синхронизации не равен", portfolioCommand.getOperation().toString(), is("SYNCHRONIZE"));
        assertThat("ключ команды по синхронизации ведомого  не равен", key, is(contractIdSlave));
//        assertThat("дата команды по синхронизации ведомого не равен", dateFromCommandWithMinut, is(dateNow));
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyIdMaster);
        assertThat("Версия последнего портфеля ведущего не равна", masterPortfolio.getVersion(), is(version + 1));
        assertThat("quantity по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getQuantity().toString(), is("5000.0"));
        assertThat("changed_at по базовой валюте не равен", masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker позиции не равен", masterPortfolio.getPositions().get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccountPos позиции не равен", masterPortfolio.getPositions().get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", masterPortfolio.getPositions().get(0).getQuantity().toString(), is("0"));
        assertThat("ChangedAt позиции не равен", masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("last_change_detected_version позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeDetectedVersion(), is(version + 1));
        assertThat("LastChangeAction позиции не равен", masterPortfolio.getPositions().get(0).getLastChangeAction().toString(), is("11"));
        version = version + 1;
        masterSignal = masterSignalDao.getMasterSignalByVersion(strategyIdMaster, version);
        assertThat("Action сигнала не равен", masterSignal.getAction().toString(), is("11"));
        assertThat("Время создания сигнала не равно", masterSignal.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(now.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("цена за бумагу в сигнале не равна", masterSignal.getPrice().longValue(), is(priceS.getUnscaled()));
        assertThat("количество ед. актива не равно", masterSignal.getQuantity().longValue(), is(quantityS.getUnscaled()));
        assertThat("ticker бумаги в сигнале не равен", masterSignal.getTicker(), is(ticker));
        assertThat("tradingClearingAccount бумаги в сигнале не равен", masterSignal.getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Состояние сигнала  не равно", masterSignal.getState().toString(), is("1"));
    }


    /////////***методы для работы тестов**************************************************************************


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

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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
    Tracking.PortfolioCommand createActualizeCommandToTrackingMasterCommandNoBaseMoney(String contractId, OffsetDateTime time, int version,
                                                                                       long unscaled, int scale, Tracking.Decimal price,
                                                                                       Tracking.Decimal quantityS) {
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
}
