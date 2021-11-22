package stpTrackingSlave.handleSynchronizeCommand.AnalyzePortfolio;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
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
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;


@Slf4j
@Epic("handleSynchronizeCommand -Анализ портфеля и фиксация результата")
@Feature("TAP-7930")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class
})
public class AnalyzePortfolioTest {

    @Autowired
    StringToByteSenderService kafkaSender;
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
    @Autowired
    StpTrackingSlaveSteps steps;

    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    Subscription subscription;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-KHGHC74O";
    String SIEBEL_ID_SLAVE = "5-21256A3ZA";
    long subscriptionId;


    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";

    public String tickerUSD = "USDRUB";
    public String tradingClearingAccountUSD = "MB9885503216";

    public String tickerGBP = "GBPRUB";
    public String tradingClearingAccountGBP = "MB9885503216";

    public String quantityUSD = "275";
    public String classCodeUSD = "EES_CETS";

    String tickerSBER = "SBER";
    String tradingClearingAccountSBER = "L01+00002F00";
    String classCodeSBER = "TQBR";


    String tickerABBV = "ABBV";
    String classABBV = "SPBXM";
    String tradingClearingAccountABBV = "TKCBM_TCAB";

    String tickerVTBperp = "VTBperp";
    String tradingClearingAccountVTBperp = "TKCBM_TCAB";
    String classCodeVTBperp = "SPBBND";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(steps.clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(steps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientMaster);
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
            try {
                steps.createEventInSubscriptionEvent(contractIdSlave, strategyId, subscriptionId);
            } catch (Exception e) {
            }
        });
    }

    @SneakyThrows
    @Test
    @AllureId("681845")
    @DisplayName("C681845.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C681845() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave,  contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "3657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(position.get(0).getQuantity().multiply(price));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "0", nullValue());
    }




    @SneakyThrows
    @Test
    @AllureId("683302")
    @DisplayName("C683302.AnalyzePortfolio.Набор позиций slave-портфеля, позиции в slave_portfolio и в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C683302() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();


//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster,null, contractIdMaster,null,  ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "3.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "4893.36";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 1, new BigDecimal("221"), new BigDecimal("0"),
            new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(position.get(0).getQuantity().multiply(price));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "2.0", notNullValue());
        assertThat("ChangedAt позиции в портфеле slave не равен", position.get(0).getChangedAt().toInstant(), is(date.toInstant()));
    }



    @SneakyThrows
    @Test
    @AllureId("684579")
    @DisplayName("C684579.AnalyzePortfolio.Набор позиций slave-портфеля, позиции есть в slave_portfolio, но нет в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C684579() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerABBV, tradingClearingAccountABBV,
            "3.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "4873.36", masterPos);
//        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "5364.78";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 1, new BigDecimal("221"), new BigDecimal("0"),
            new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceMaster = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerABBV, tradingClearingAccountABBV, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceMaster);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateABBV = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateAAPL = new BigDecimal("0");
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(positionABBV.get(0).getQuantity().multiply(priceMaster))
            .add(positionAAPL.get(0).getQuantity().multiply(price));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(positionABBV, priceMaster, slavePortfolioValue, masterPositionRateABBV, tickerABBV, tradingClearingAccountABBV, "0", nullValue());
        checkPosition(positionAAPL, price, slavePortfolioValue, masterPositionRateAAPL, ticker, tradingClearingAccount, "2.0", notNullValue());
        assertThat("ChangedAt позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }


    @SneakyThrows
    @Test
    @AllureId("688348")
    @DisplayName("C688348.AnalyzePortfolio.Анализ портфеля.Набор позиций slave-портфеля по облигациям")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C688348() {
        List<String> list = steps.getPriceFromExchangePositionCache(tickerVTBperp, tradingClearingAccountVTBperp, SIEBEL_ID_MASTER);
        String aci = list.get(0);
        String nominal = list.get(1);
        String minPrIncrement = list.get(2);
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerVTBperp, tradingClearingAccountVTBperp,
            "2.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "3657.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal currentNominal = new BigDecimal(nominal);
        BigDecimal minPriceIncrement = new BigDecimal(minPrIncrement);
        BigDecimal aciValue = new BigDecimal(aci);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal getprice = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerVTBperp, tradingClearingAccountVTBperp, "last", SIEBEL_ID_SLAVE));
        //расчитываетм price
        BigDecimal priceBefore = getprice.multiply(currentNominal)
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(currentNominal)
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price = roundPrice
            .add(aciValue);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerVTBperp))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(position.get(0).getQuantity().multiply(price));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, tickerVTBperp, tradingClearingAccountVTBperp, "0", nullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1323457")
    @DisplayName("C1323457.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio. Для валютных позиций USD")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1323457() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        String currentNominal = "1";
        String minPriceIncrement = "0.0025";
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER,
            "10", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerUSD, tradingClearingAccountUSD,
            "39", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceUSD = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerUSD, tradingClearingAccountUSD, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceNewUSD = priceUSD.divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal newMinPriceIncrement = new BigDecimal(minPriceIncrement).divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal roundPriceNew = priceNewUSD.divide(newMinPriceIncrement, 0, RoundingMode.HALF_UP)
            .multiply(newMinPriceIncrement).stripTrailingZeros();
        BigDecimal priceSBER = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerSBER, tradingClearingAccountSBER, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceSBER);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateSBER = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateUSD = new BigDecimal("0");
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionSBER = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerSBER))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionUSD = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerUSD))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(positionSBER.get(0).getQuantity().multiply(priceSBER))
            .add(positionUSD.get(0).getQuantity().multiply(priceNewUSD));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(positionSBER, priceSBER, slavePortfolioValue, masterPositionRateSBER, tickerSBER, tradingClearingAccountSBER, "0", nullValue());
        checkPosition(positionUSD, roundPriceNew, slavePortfolioValue, masterPositionRateUSD, tickerUSD, tradingClearingAccountUSD, "39", notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1346546")
    @DisplayName("C1346546.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio.Для валютных позиций GBP")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1346546() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " + String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        String currentNominal = "1";
        String minPriceIncrement = "0.0025";
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER,
            "10", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerGBP, tradingClearingAccountGBP,
            "39", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceGBP = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerGBP, tradingClearingAccountGBP, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceNewGBP = priceGBP.divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal newMinPriceIncrement = new BigDecimal(minPriceIncrement).divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal roundPriceNew = priceNewGBP.divide(newMinPriceIncrement, 0, RoundingMode.HALF_UP)
            .multiply(newMinPriceIncrement).stripTrailingZeros();
        BigDecimal priceSBER = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerSBER, tradingClearingAccountSBER, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceSBER);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateSBER = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateUSD = new BigDecimal("0");
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionSBER = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerSBER))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionUSD = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerGBP))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(positionSBER.get(0).getQuantity().multiply(priceSBER))
            .add(positionUSD.get(0).getQuantity().multiply(priceNewGBP));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(positionSBER, priceSBER, slavePortfolioValue, masterPositionRateSBER, tickerSBER, tradingClearingAccountSBER, "0", nullValue());
        checkPosition(positionUSD, roundPriceNew, slavePortfolioValue, masterPositionRateUSD, tickerGBP, tradingClearingAccountGBP, "39", notNullValue());
    }





    // методы для работы тестов*************************************************************************
    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = steps.createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }

    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandSynTrackingSlaveCommand(String contractIdSlave, OffsetDateTime time) {
        //создаем команду
        Tracking.PortfolioCommand command = steps.createCommandSynchronize(contractIdSlave, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
    }

    //проверяем параметры позиции
    public void checkPosition(List<SlavePortfolio.Position> position, BigDecimal price, BigDecimal slavePortfolioValue,
                              BigDecimal masterPositionRate, String ticker, String tradingClearingAccount, String quantity,
                              Matcher<Object> changedAt) {
        BigDecimal slavePositionRate = new BigDecimal(quantity).multiply(price)
            .divide(slavePortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal slavePositionRateDiff = masterPositionRate.subtract(slavePositionRate);
        BigDecimal slavePositionQuantityDiff = slavePositionRateDiff.multiply(slavePortfolioValue)
            .divide(price, 4, BigDecimal.ROUND_HALF_UP);
        assertThat("ticker бумаги позиции в портфеле slave не равна", position.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", position.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", position.get(0).getQuantity().toString(), is(quantity));
        assertThat("Price позиции в портфеле slave не равен", position.get(0).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", position.get(0).getRate().doubleValue(), is(slavePositionRate.doubleValue()));
        assertThat("RateDiff позиции в портфеле slave не равен", position.get(0).getRateDiff(), is(slavePositionRateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", position.get(0).getQuantityDiff(), is(slavePositionQuantityDiff));
        assertThat("ChangedAt позиции в портфеле slave не равен", position.get(0).getChangedAt(), is(changedAt));
    }



   // ожидаем версию портфеля slave
    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }
}
