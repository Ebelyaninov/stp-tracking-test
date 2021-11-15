package stpTrackingSlave.handleSynchronizeCommand.AnalyzePortfolio;

import com.google.protobuf.Timestamp;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
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
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.*;


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
    @Autowired
    MiddleGrpcService middleGrpcService;

    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    Subscription subscription;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    SlaveOrder slaveOrder;
    UUID strategyId;


    String SIEBEL_ID_MASTER = "1-C8AX9FE";
    String SIEBEL_ID_SLAVE = "1-BXDUEON";
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
    String tickerBond = "ALFAperp";
    String tradingClearingAccountBond = "TKCBM_TCAB";
    String classCodeBond = "SPBBND";


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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
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
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
            "5.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "4893.36";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(ticker, tradingClearingAccount,
            "2.0", date, 1, new BigDecimal("107"), new BigDecimal("0"),
            new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
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
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
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
            "2.0", date, 1, new BigDecimal("107"), new BigDecimal("0"),
            new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
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
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
    }




    private static Stream<Arguments> provideFlagNotChange() {
        return Stream.of(
            Arguments.of(true, true, true, true),
            Arguments.of(false, false, false, false),
            Arguments.of(false, true, false, true),
            Arguments.of(true, false, true, false )
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideFlagNotChange")
    @AllureId("1439728")
    @DisplayName("C1439728.AnalyzePortfolio.Набор позиций slave-портфеля, позиции есть в slave_portfolio, но нет в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1439728(Boolean buy, Boolean sell, Boolean buyRes, Boolean sellRes) {
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "2", date, null, new BigDecimal("108.53"),
            new BigDecimal("0.0235"), new BigDecimal("0.025500"), new BigDecimal("2.1656"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(sellRes));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
    }



    @SneakyThrows
    @Test
    @AllureId("688348")
    @DisplayName("C688348.AnalyzePortfolio.Анализ портфеля.Набор позиций slave-портфеля по облигациям")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C688348() {
        List<String> list = steps.getPriceFromExchangePositionCache(tickerBond, tradingClearingAccountBond, SIEBEL_ID_MASTER);
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerBond, tradingClearingAccountBond,
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
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal currentNominal = new BigDecimal(nominal);
        BigDecimal minPriceIncrement = new BigDecimal(minPrIncrement);
        BigDecimal aciValue = new BigDecimal(aci);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal getprice = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerBond, tradingClearingAccountBond, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(tickerBond))
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
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, tickerBond, tradingClearingAccountBond, "0", nullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
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
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
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
        assertThat("Проверяем флаг buy_enabled", positionUSD.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionUSD.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
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
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
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
        List<SlavePortfolio.Position> positionGBP = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerGBP))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(positionSBER.get(0).getQuantity().multiply(priceSBER))
            .add(positionGBP.get(0).getQuantity().multiply(priceNewGBP));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(positionSBER, priceSBER, slavePortfolioValue, masterPositionRateSBER, tickerSBER, tradingClearingAccountSBER, "0", nullValue());
        checkPosition(positionGBP, roundPriceNew, slavePortfolioValue, masterPositionRateUSD, tickerGBP, tradingClearingAccountGBP, "39", notNullValue());
        assertThat("Проверяем флаг buy_enabled", positionGBP.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionGBP.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1382257")
    @DisplayName("C1382257. Флаги buy_enabled и sell_enabled у позиций не заполнены (нет записи)")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1382257() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 1, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }




    @SneakyThrows
    @Test
    @AllureId("1439616")
    @DisplayName("C1439616.Флаги buy_enabled и sell_enabled у позиции, которой нет у мастера на инициализации портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1439616() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerABBV, tradingClearingAccountABBV,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //формируем команду на актуализацию для slave
        //передаем  базовую валюту и позицию, которой нет у мастера
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            1234, steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
    }



    @SneakyThrows
    @Test
    @AllureId("1382266")
    @DisplayName("C1382266. Проставляем флаг buy_enabled = true для operation = 'ACTUALIZE' и action = 'ADJUST'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    void C1382266() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "6551.10",  positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(ticker, tradingClearingAccount,
            "3", date, false, false);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(2, 988486,
            contractIdSlave, 3,  time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1387788")
    @DisplayName("C1387788. Проставляем значение флагов на false событие operation = 'ACTUALIZE' и action = 'ADJUST' и завели 0")
    @Subfeature("Успешные сценарии")
    @Description("Получили событие с baseMoneyPosition = 0")
    void C1387788() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "6551.10",  positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(ticker, tradingClearingAccount,
            "0", date, true, true);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "7000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(2, 001,
            contractIdSlave, 3,  time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("1387789")
    @DisplayName("C1387789. Определяем buy_enabled = false с action = MONEY_SELL_TRADE и оба флага у позиции включены ")
    @Subfeature("Успешные сценарии")
    @Description("Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version = slave_portfolio.compared_to_master_version. lots после округления < 0 " +
        "И buy_enabled = true")
    void C1387789() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99",  positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(tickerSBER, tradingClearingAccountSBER,
            "10", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD,
            "10", true, true, tickerSBER, tradingClearingAccountSBER, "20", true, true, date);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "9900.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 600086,
            contractIdSlave, 3, steps.createPosInCommand(tickerUSD, tradingClearingAccountUSD, 5,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1385944")
    @DisplayName("C1385944. Operation = 'ACTUALIZE'.Action =MONEY_SELL_TRADE. Master_portfolio.version > slave_portfolio.compared_to_master_version. lots после округления < 0")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version = slave_portfolio.compared_to_master_version. Позиция > 0, lots после округления < 0 " +
        "И buy_enabled = true")
    void C1385944() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "10", tickerUSD, tradingClearingAccountUSD,"0", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "10", tickerUSD, tradingClearingAccountUSD,"10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD,
            "10", false, null, tickerSBER, classCodeSBER, "20", true, true, date);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "9900.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 1100086,
            contractIdSlave, 3, steps.createPosInCommand(tickerUSD, tradingClearingAccountUSD, 5,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }



    @SneakyThrows
    @Test
    @AllureId("1385944")
    @DisplayName("C1385944. Не достаточно средств докупить бумагу")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'SECURITY_BUY_TRADE'. " +
        "Master_portfolio.version = slave_portfolio.compared_to_master_version.  lots после округления = 0 " +
        "И buy_enabled = true")
    void C1385945() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "20", tickerUSD, tradingClearingAccountUSD,"10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "30", tickerUSD, tradingClearingAccountUSD,"10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD,
            "0", false, null, tickerSBER, classCodeSBER, "0", true, true, date);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "9900.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 001,
            contractIdSlave, 3, steps.createPosInCommand(tickerSBER, tradingClearingAccountSBER, 10,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(1).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(1).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1385949")
    @DisplayName("C1385949. Operation = 'ACTUALIZE'.Action =MONEY_SELL_TRADE. Master_portfolio.version > slave_portfolio.compared_to_master_version. Позиция < 0. lots после округления < 0")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version > slave_portfolio.compared_to_master_version. Позиция < 0  lots после округления < 0 " +
        "И buy_enabled = true")
    void C1385949() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "10", tickerUSD, tradingClearingAccountUSD,"10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "10", tickerUSD, tradingClearingAccountUSD,"5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD,
            "5", null, false, tickerSBER, classCodeSBER, "20", false, false, date);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "9900.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 1100086,
            contractIdSlave, 3, steps.createPosInCommand(tickerUSD, tradingClearingAccountUSD, 5,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1387785")
    @DisplayName("C1387785. Operation = 'ACTUALIZE'.Action =MONEY_SELL_TRADE. Master_portfolio.version > slave_portfolio.compared_to_master_version. Позиция = 0. lots после округления = 0")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version > slave_portfolio.compared_to_master_version. Позиция = 0  lots после округления = 0 ")
    void C1387785() {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "description test стратегия autotest update adjust base currency";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "10", tickerUSD, tradingClearingAccountUSD,"10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(tickerSBER, tradingClearingAccountSBER,
            "10", tickerUSD, tradingClearingAccountUSD,"10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(tickerUSD, tradingClearingAccountUSD,
            "10", true, true, tickerSBER, tradingClearingAccountSBER, "20", false, false, date);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "9900.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 1100086,
            contractIdSlave, 3, steps.createPosInCommand(tickerUSD, tradingClearingAccountUSD, 0,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }



    private static Stream<Arguments> provideSecurityBuyTradeResultFalse() {
        return Stream.of(
            Arguments.of(false, false),
            Arguments.of(true, true),
            Arguments.of(true, false)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSecurityBuyTradeResultFalse")
    @AllureId("1403631.Operation = 'ACTUALIZE'.Action ='SECURITY_BUY_TRADE'.Master_portfolio.version = slave_portfolio.compared_to_master_version. " +
        "Нет позиции для синхронизации")
    @DisplayName("C1403631")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1403631(Boolean buy, Boolean sell) {
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoney = "0";
        List<SlavePortfolio.Position> createListSlaveOnePosOld = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("108.53"),
            new BigDecimal("0"), new BigDecimal("0.0765"), new BigDecimal("0.0000"), false, false);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 0, 2,
            baseMoney, date, createListSlaveOnePosOld);
        //создаем портфель для ведомого
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0"), new BigDecimal("0.0760"), new BigDecimal("4.936"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 644555, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkSlavePortfolioVersion(2);
//        await().atMost(FIVE_SECONDS).until(() ->
//            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,2), notNullValue());
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
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6445.55"));
        //проверяем параметры позиции с расчетами
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "5", notNullValue());

    }


    private static Stream<Arguments> provideSecuritySellTradeResultFalse() {
        return Stream.of(
            Arguments.of(false, false),
            Arguments.of(true, true),
            Arguments.of(false, true)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSecuritySellTradeResultFalse")
    @AllureId("1404387.Operation = 'ACTUALIZE'.Action =SECURITY_SELL_TRADE.Master_portfolio.version = slave_portfolio.compared_to_master_version" +
        "Нет позиции для синхронизации")
    @DisplayName("C1404387")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1404387(Boolean buy, Boolean sell) {
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 742164, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 6, Tracking.Portfolio.Action.SECURITY_SELL_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkSlavePortfolioVersion(2);
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
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("7421.64"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "6", notNullValue());
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    private static Stream<Arguments> provideSecurityBuyTradeResultBuyTrue() {
        return Stream.of(
            Arguments.of(true, true, true, false),
            Arguments.of(true, false, true, false),
            Arguments.of(false, false, false, false),
            Arguments.of(false, true, false, false )
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSecurityBuyTradeResultBuyTrue")
    @AllureId("1404566.Operation = 'ACTUALIZE'.Action ='SECURITY_BUY_TRADE'.Master_portfolio.version = slave_portfolio.compared_to_master_version." +
        "lots после округления > 0 И buy_enabled = true")
    @DisplayName("C404566")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1404566(Boolean buy, Boolean sell, Boolean buyRes, Boolean sellRes) {
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoney = "0";
        List<SlavePortfolio.Position> createListSlaveOnePosOld = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("108.53"),
            new BigDecimal("0"), new BigDecimal("0.0765"), new BigDecimal("0.0000"), false, false);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 0, 2,
            baseMoney, date, createListSlaveOnePosOld);
        //создаем портфель для ведомого
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0"), new BigDecimal("0.0760"), new BigDecimal("4.936"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 1, 1,
            0, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("110.15"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 677970, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 2, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId,2), notNullValue());
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
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6779.70"));
         //проверяем параметры позиции с расчетами
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(sellRes));
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "2", notNullValue());
    }



    private static Stream<Arguments> provideSecuritySellTradeResultSellTrue() {
        return Stream.of(
            Arguments.of(true, true, false, true),
            Arguments.of(true, false, false, false),
            Arguments.of(false, false, false, false),
            Arguments.of(false, true, false, true));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSecuritySellTradeResultSellTrue")
    @AllureId("1406380.Operation = 'ACTUALIZE'.Action ='SECURITY_SELL_TRADE'. " +
        " и master_portfolio.version =slave_portfolio.compared_to_master_version")
    @DisplayName("C1406380")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1406380(Boolean buy, Boolean sell, Boolean buyRes, Boolean sellRes) {
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 1, 1,
            1, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("110.15"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 721124, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 8, Tracking.Portfolio.Action.SECURITY_SELL_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkSlavePortfolioVersion(2);
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
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("7211.24"));
            //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "8", notNullValue());
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(sellRes));
    }


    private static Stream<Arguments> provideSecurityBuyTradeResultBuy() {
        return Stream.of(
            Arguments.of(true, true, true, false),
            Arguments.of(false, false, true, false),
            Arguments.of(false, true, true, true),
            Arguments.of(true, false, true, false )
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSecurityBuyTradeResultBuy")
    @AllureId("1387797")
    @DisplayName("C1387797.Operation = 'ACTUALIZE'.ACTION = 'SECURITY_BUY_TRADE'." +
        "Master_portfolio.version > slave_portfolio.compared_to_master_version.Знак изменений > 0." +
        "Доступна позиция для синхронизации")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1387797(Boolean buy, Boolean sell, Boolean buyRes, Boolean sellRes) {
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        List<MasterPortfolio.Position> masterPosNew = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "5675.1", masterPosNew);
        List<MasterPortfolio.Position> masterPosLast = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "7", tickerABBV, tradingClearingAccountABBV, "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "5459.1", masterPosLast);
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoney = "0";
        List<SlavePortfolio.Position> createListSlaveOnePosOld = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("108.53"),
            new BigDecimal("0"), new BigDecimal("0.0765"), new BigDecimal("0.0000"), false, false);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 0, 2,
            baseMoney, date, createListSlaveOnePosOld);
        //создаем портфель для ведомого
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0"), new BigDecimal("0.0760"), new BigDecimal("4.936"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 644555, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerABBV, tradingClearingAccountABBV, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkSlavePortfolioVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPosQuantityABBV = masterPortfolio.getPositions().get(1).getQuantity().multiply(priceABBV);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPosQuantityABBV).add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateABBV = masterPosQuantityABBV.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(position.get(0).getQuantity().multiply(price))
            .add(positionABBV.get(0).getQuantity().multiply(priceABBV));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6445.55"));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "5", notNullValue());
        //проверяем параметры позиции с расчетами
        checkPosition(positionABBV, priceABBV, slavePortfolioValue, masterPositionRateABBV, tickerABBV, tradingClearingAccountABBV, "0", nullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(sellRes));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
    }




    private static Stream<Arguments> provideSecuritySellTradeResultSell() {
        return Stream.of(
            Arguments.of(true, true, false, true),
            Arguments.of(false, false, false, true),
            Arguments.of(false, true, false, true),
            Arguments.of(true, false, true, true )
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSecuritySellTradeResultSell")
    @AllureId("1408252")
    @DisplayName("C1408252.Operation = 'ACTUALIZE'.ACTION = 'SECURITY_SELL_TRADE'.Master_portfolio.version > slave_portfolio.compared_to_master_version." +
        "Знак изменений < 0.Доступна позиция для синхронизации")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для анализа slave-портфеля на основе текущего портфеля master'а и фиксации полученных результатов.")
    void C1408252(Boolean buy, Boolean sell, Boolean buyRes, Boolean sellRes) {
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
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
          //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        List<MasterPortfolio.Position> masterPosNew = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "5", tickerABBV, tradingClearingAccountABBV, "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "5675.1", masterPosNew);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(ticker, tradingClearingAccount,
            "3", tickerABBV, tradingClearingAccountABBV, "3", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_SELL_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "5892.1", masterPos);
        //создаем портфель для slave в cassandra
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(ticker, tradingClearingAccount,
            "10", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        slaveOrderDao.insertIntoSlaveOrderWithFilledQuantity(contractIdSlave, strategyId, 1, 1,
            1, classCode, new BigDecimal("0"), UUID.randomUUID(), new BigDecimal("110.15"), new BigDecimal("5"),
            null, ticker, tradingClearingAccount);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 721124, contractIdSlave,
            2, steps.createPosInCommand(ticker, tradingClearingAccount, 8, Tracking.Portfolio.Action.SECURITY_SELL_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(ticker, tradingClearingAccount, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(tickerABBV, tradingClearingAccountABBV, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkSlavePortfolioVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPosQuantityABBV = masterPortfolio.getPositions().get(1).getQuantity().multiply(priceABBV);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPosQuantityABBV).add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateABBV = masterPosQuantityABBV.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(ticker))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePortfolioValue = slavePortfolio.getBaseMoneyPosition().getQuantity()
            .add(position.get(0).getQuantity().multiply(price))
            .add(positionABBV.get(0).getQuantity().multiply(priceABBV));
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("7211.24"));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, masterPositionRate, ticker, tradingClearingAccount, "8", notNullValue());
        //проверяем параметры позиции с расчетами
        checkPosition(positionABBV, priceABBV, slavePortfolioValue, masterPositionRateABBV, tickerABBV, tradingClearingAccountABBV, "0", nullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(sellRes));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
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

    // ожидаем версию портфеля slave
    void checkSlavePortfolioVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, version);
            if (slavePortfolio.getVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }

    Tracking.PortfolioCommand createCommandActualizeOnlyBaseMoney(int scale, int unscaled, String contractIdSlave,
                                                                  int version, OffsetDateTime time,
                                                                  Tracking.Portfolio.Action action, boolean delayedCorrection) {
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
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }


    Tracking.PortfolioCommand createCommandActualizeWithPosition(int scale, int unscaled, String contractIdSlave,
                                                                 int version, Tracking.Portfolio.Position position,
                                                                 OffsetDateTime time, Tracking.Portfolio.Action action,
                                                                 boolean delayedCorrection) {
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
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }
}
