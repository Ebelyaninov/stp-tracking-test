package stpTrackingMaster.handleActualizeCommand.saveDividend;



import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.Dividend;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.DividentDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.entities.enums.SubscriptionStatus;
import ru.qa.tinkoff.tracking.services.allure.AllureMasterStepsConfiguration;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;

@Slf4j
@Epic("saveDividend Начисление дивидендов в виртуальный портфель")
@Feature("ISTAP-4415")
@DisplayName("stp-tracking-master")
@Tags({@Tag("stp-tracking-master"), @Tag("handleActualizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingMasterStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    AllureMasterStepsConfiguration.class
})
public class SaveDividentTest {

    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingMasterSteps steps;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    DividentDao dividentDao;
    @Autowired
    StpInstrument instrument;


    MasterPortfolio masterPortfolio;
    String contractIdMaster;
    String contractIdSlave;
    int version;
    String siebelIdMaster;
    String siebelIdSlave;

    String ticker;
    String tradingClearingAccount;

    String title;
    String description;
    UUID strategyId;
    UUID investIdMaster;
    UUID investIdSlave;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        ticker = instrument.tickerXS0587031096;
        tradingClearingAccount = instrument.tradingClearingAccountXS0587031096;
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
        siebelIdSlave = stpSiebel.siebelIdSlaveStpTrackingMaster;
        int randomNumber = 0 + (int) (Math.random() * 1000);
        title = "Autotest " + String.valueOf(randomNumber);
        description = "new test стратегия autotest";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slaveActiveSubscription в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlaveActive =steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlaveActive.getInvestId();
        contractIdSlave = resAccountSlaveActive.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelIdSlave);
        steps.deleteDataFromDb(siebelIdMaster);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                dividentDao.deleteAllDividendByContractAndStrategyId(contractIdMaster, strategyId);
            } catch (Exception e){
            }
        });
    }

    private static Stream<Arguments> strategyStatus() {
        return Stream.of(
            Arguments.of(StrategyStatus.active),
            Arguments.of(StrategyStatus.frozen)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("strategyStatus")
    @AllureId("1858514")
    @DisplayName("C1858514.HandleActualizeCommand.SaveDividend Начисляем первый дивиденд мастеру")
    @Subfeature("Успешные сценарии")
    @Description("saveDividend Начисление дивидендов в виртуальный портфель")
    void C1858514(StrategyStatus strategyStatus) {
        UUID instrumentUID = steps.getInstrumentUID(ticker, instrument.classCodeXS0587031096);
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        BigDecimal baseMoney = new BigDecimal("4985.0");
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            strategyStatus, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        int versionPos = version - 1;
        BigDecimal baseMoneyPortfolio = new BigDecimal("4990.0");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Date dateMinus10Minutes = Date.from(utc.minusMinutes(10).toInstant());

        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        List<MasterPortfolio.Position> emptyList =  new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .lastChangeDetectedVersion(versionPos)
            .changedAt(date)
            .quantity(new BigDecimal("1"))
                .positionId(instrumentUID)
            .build());
        createMasterPortfolioWithPosition(emptyList, "2000", dateMinus10Minutes, version -1);
        createMasterPortfolioWithPosition(positionList, baseMoneyPortfolio.toString(), date, version);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);

        //формируем команду на актуализацию по ведущему по дивидентам
        Tracking.PortfolioCommand command = steps.createSaveDividendMasterCommand(contractIdMaster, now, 1002, 2,  ticker, tradingClearingAccount, 1L, Tracking.Currency.USD);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).until(() ->
            dividentDao.findAllDividend(contractIdMaster, strategyId), notNullValue());
        //Смотрим, сообщение, которое поймали в топике kafka
        ckeckIfMessageWasSent(contractIdSlave);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        BigDecimal amount = BigDecimal.valueOf(command.getDividend().getAmount().getUnscaled(), command.getDividend().getAmount().getScale());
        BigDecimal newBasemoneyPosition = baseMoneyPortfolio.add(amount);
        Dividend getDividend = dividentDao.findAllDividend(contractIdMaster, strategyId).get(0);
        checkMasterPortfolio(masterPortfolio, positionList, version + 1, newBasemoneyPosition);
        //Проверяем добавление записи в таблицу dividend
        checkDividend(command, getDividend, now, amount);
    }


    @SneakyThrows
    @Test
    @AllureId("1858511")
    @DisplayName("C1858511.HandleActualizeCommand.SaveDividend Нашли уже обработанный дивиденд")
    @Subfeature("Успешные сценарии")
    @Description("saveDividend Начисление дивидендов в виртуальный портфель")
    void C1858511() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        // создаем   портфель ведущего  в кассандре c позицией
        int versionPos = version - 1;
        BigDecimal baseMoneyPortfolio = new BigDecimal("5000.02");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Date dateMinus10Minutes = Date.from(utc.minusMinutes(10).toInstant());

        List<MasterPortfolio.Position> emptyList =  new ArrayList<>();
        List<MasterPortfolio.Position> positionList = createPosition(ticker, tradingClearingAccount, date, versionPos, "1");
        createMasterPortfolioWithPosition(emptyList, "2000", dateMinus10Minutes, version -1);
        createMasterPortfolioWithPosition(positionList, baseMoneyPortfolio.toString(), date, version);
        Dividend.Context context = createContext(ticker, tradingClearingAccount,  4, new BigDecimal("10.02"), now);
        dividentDao.insertIntoDividend(contractIdMaster, strategyId, 1L, context);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему по дивидентам
        Tracking.PortfolioCommand command = steps.createSaveDividendMasterCommand(contractIdMaster, now, 10002, 2,  ticker, tradingClearingAccount, 1L, Tracking.Currency.USD);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).until(() ->
            dividentDao.findAllDividend(contractIdMaster, strategyId), notNullValue());
        //Смотрим, сообщение, которое поймали в топике kafka
        ckeckIfMessageWasSent(contractIdSlave);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        Dividend getDividend = dividentDao.findAllDividend(contractIdMaster, strategyId).get(0);
        checkMasterPortfolio(masterPortfolio, positionList, version, baseMoneyPortfolio);
        BigDecimal amount = new BigDecimal("10.02");
        //Проверяем добавление записи в таблицу dividend
        checkDividend(command, getDividend, now, amount);
    }

    @SneakyThrows
    @Test
    @AllureId("1860478")
    @DisplayName("C1860478.HandleActualizeCommand.SaveDividend Нашли несколько дивидендов в БД и успешно начислили новый")
    @Subfeature("Успешные сценарии")
    @Description("saveDividend Начисление дивидендов в виртуальный портфель")
    void C1860478() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "1";
        int versionPos = version - 1;
        BigDecimal baseMoneyPortfolio = new BigDecimal("5000.02");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Date dateMinus10Minutes = Date.from(utc.minusMinutes(10).toInstant());

        List<MasterPortfolio.Position> emptyList =  new ArrayList<>();
        List<MasterPortfolio.Position> positionList = createPosition(ticker, tradingClearingAccount, date, versionPos, quantityPos);
        createMasterPortfolioWithPosition(emptyList, "2000", dateMinus10Minutes, version -1);
        createMasterPortfolioWithPosition(positionList, baseMoneyPortfolio.toString(), date, version);
        Dividend.Context secondContext = createContext(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,  4, new BigDecimal("10.02"), now);
        Dividend.Context thirdContext = createContext(instrument.tickerFB, instrument.tradingClearingAccountFB,  4, new BigDecimal("10.02"), now);
        dividentDao.insertIntoDividend(contractIdMaster, strategyId, 21L, secondContext);
        dividentDao.insertIntoDividend(contractIdMaster, strategyId, 32L, thirdContext);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему по дивидентам
        Tracking.PortfolioCommand command = steps.createSaveDividendMasterCommand(contractIdMaster, now, 10002, 2,  ticker, tradingClearingAccount, 1L, Tracking.Currency.RUB);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).until(() ->
            dividentDao.findAllDividend(contractIdMaster, strategyId).stream()
                .filter(context -> context.getContext().getExchangePositionId().getTicker().equals(ticker))
                .collect(Collectors.toList()), notNullValue());
        //Смотрим, сообщение, которое поймали в топике kafka
        ckeckIfMessageWasSent(contractIdSlave);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        Dividend getDividend = dividentDao.findAllDividend(contractIdMaster, strategyId).stream()
            .filter(context -> context.getContext().getExchangePositionId().getTicker().equals(ticker))
            .collect(Collectors.toList()).get(0);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        BigDecimal amount = BigDecimal.valueOf(command.getDividend().getAmount().getUnscaled(), command.getDividend().getAmount().getScale());
        BigDecimal newBasemoneyPosition = baseMoneyPortfolio.add(amount);
        checkMasterPortfolio(masterPortfolio, positionList, version + 1, newBasemoneyPosition);
        //Проверяем добавление записи в таблицу dividend
        checkDividend(command, getDividend, now, amount);
    }


    @SneakyThrows
    @Test
    @AllureId("1858512")
    @Step
    @DisplayName("C1858512.HandleActualizeCommand.SaveDividend Не нашли запись мастера")
    @Subfeature("Альтернативные сценарии")
    @Description("saveDividend Начисление дивидендов в виртуальный портфель")
    void C1858512() {
        strategyId = UUID.randomUUID();
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        version = 3;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Не создаем   портфель ведущего  в кассандре c позицией
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, false, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null);
        //формируем команду на актуализацию по ведущему по дивидентам
        Tracking.PortfolioCommand command = steps.createSaveDividendMasterCommand(contractIdMaster, now, 10002, 2,  ticker, tradingClearingAccount, 1L, Tracking.Currency.USD);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractIdMaster;
        //вычитываем из топика кафка tracking.slave.command все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).until(() ->
            dividentDao.findAllDividend(contractIdMaster, strategyId).size(), is(0));
        //Смотрим, сообщение, которое поймали в топике kafka
        ckeckIfMessageWasSent(contractIdSlave);
        List<Dividend> getDividend = dividentDao.findAllDividend(contractIdMaster, strategyId);
        assertThat("Добавили запись по дивидендам", getDividend.size(), is(0));
    }

    //создаем портфель master в cassandra с позицией
    @Step("Создаем портфель мастеру")
    void createMasterPortfolioWithPosition( List<MasterPortfolio.Position> positionList, String money, Date date, int portfolioVersion) {
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractIdMaster, strategyId, portfolioVersion, baseMoneyPosition, positionList);
    }

    @Step("Проверяем запись в таблице devidend: \n {dividend}")
    void checkDividend(Tracking.PortfolioCommand dividendCommand, Dividend dividend, OffsetDateTime now, BigDecimal amount){
        assertThat("contract_id != " + contractIdMaster, dividend.getContractId(), is(contractIdMaster));
        assertThat("strategy_id != " + strategyId, dividend.getStrategyId(), is(strategyId));
        assertThat("id != dividend.id из входных параметров" + dividendCommand.getDividend().getId(), dividend.getId(), is(dividendCommand.getDividend().getId()));
        assertThat("context.version != master_portfolio.version + 1" + version + 1, dividend.getContext().getVersion(), is(version +1));
        assertThat("context.amount  != dividend.amount из входных параметров", dividend.getContext().getAmount(), is(amount));
        assertThat("context.createdAt != created_at из входных параметров", dividend.getContext().getCreatedAt().toInstant().toString().substring(0, 22), is(now.toInstant().toString().substring(0, 22)));
        assertThat("context.exchangePositionId.ticker != ticker из входных параметров", dividend.getContext().getExchangePositionId().getTicker(), is(dividendCommand.getDividend().getExchangePositionId().getTicker()));
        assertThat("context.exchangePositionId.tradingClearingAccount != trading_clearing_account из входных параметров", dividend.getContext().getExchangePositionId().getTradingClearingAccount(), is(dividendCommand.getDividend().getExchangePositionId().getTradingClearingAccount()));
    }

    @Step("Проверяем запись в таблице masterPortfolio: \n {masterPortfolio}")
    void checkMasterPortfolio(MasterPortfolio masterPortfolio, List<MasterPortfolio.Position> positionList, int version, BigDecimal baseMoneyPosition){
        assertThat("Версия != " + version, masterPortfolio.getVersion(), is(version));
        assertThat("Не увеличили базовую валюту на значение дивидента", masterPortfolio.getBaseMoneyPosition().getQuantity(), is(baseMoneyPosition));
        assertThat("Не перенесли позицию", masterPortfolio.getPositions().toString(), is(positionList.toString()));
        assertThat("BaseMoneyPosition != ", masterPortfolio.getBaseMoneyPosition().getQuantity(), is(baseMoneyPosition));
    }

    public Dividend.Context createContext(String ticker, String tradingClearingAccount, int version, BigDecimal amount, OffsetDateTime date){
        //Создаем завод
         Dividend.ExchangePositionId exchangePositionId = new Dividend.ExchangePositionId().builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .build();
        Dividend.Context context = new Dividend.Context().builder()
            .version(version)
            .amount(amount)
            .exchangePositionId(exchangePositionId)
            .createdAt(Date.from(date.toInstant()))
            .build();
        return context;
    }

    public List<MasterPortfolio.Position> createPosition(String ticker, String tradingClearingAccount, Date date, int versionPos, String quantityPos){
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .lastChangeAction((byte)  Tracking.Portfolio.Action.SECURITY_BUY_TRADE_VALUE)
            .lastChangeDetectedVersion(versionPos)
            .changedAt(date)
            .quantity(new BigDecimal(quantityPos))
            .build());
        return positionList;
    }

    void ckeckIfMessageWasSent (String contractIdSlave){
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(5)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        assertThat("Отправили событие в топик", messages.size(), is(0));
    }
}
