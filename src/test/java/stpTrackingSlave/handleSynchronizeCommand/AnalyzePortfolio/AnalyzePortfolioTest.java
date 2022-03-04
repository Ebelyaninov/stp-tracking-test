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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.mocks.steps.MocksBasicSteps;
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMockSlaveDateConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMockSlave.StpMockSlaveDate;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
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

import static com.google.common.collect.Comparators.max;
import static com.google.common.collect.Comparators.min;
import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
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
@Tags({@Tag("stp-tracking-slave"), @Tag("analyzePortfolio")})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    MocksBasicStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    MocksBasicStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingMockSlaveDateConfiguration.class

})
public class AnalyzePortfolioTest {

    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringSenderService stringSenderService;
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
    SlaveOrder2Dao slaveOrder2Dao;
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
    @Autowired
    StpInstrument instrument;
    @Autowired
    MocksBasicSteps mocksBasicSteps;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpMockSlaveDate stpMockSlaveDate;

    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    Subscription subscription;
    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    SlaveOrder2 slaveOrder2;
    UUID strategyId;
    UUID id;

    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;


    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdAnalyzeMaster;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdAnalyzeSlave;
    }


    long subscriptionId;

    BigDecimal targetFeeReserveRate = new BigDecimal("0.03");
    String description = "description test стратегия autotest analyzeSlavePortfolio";

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
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
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
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C681845.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C681845() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Buy", "1", "1");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
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
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", nullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("683302")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C683302.AnalyzePortfolio.Набор позиций slave-портфеля, позиции в slave_portfolio и в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C683302() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Buy", "1", "1");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "4893.36";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", date, 1, new BigDecimal("107"),
            new BigDecimal("0"), new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", notNullValue());
        assertThat("ChangedAt позиции в портфеле slave не равен", position.get(0).getChangedAt().toInstant(), is(date.toInstant()));
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("684579")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C684579.AnalyzePortfolio.Набор позиций slave-портфеля, позиции есть в slave_portfolio, но нет в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C684579() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerABBV, instrument.classCodeABBV,
            "292", "289.4", "292");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Buy", "1", "1");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3.0", date, 3, positionAction);
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", date, 1, new BigDecimal("107"),
            new BigDecimal("0"), new BigDecimal("0.0487"), new BigDecimal("2"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.instrumentAAPL, "last"));
        BigDecimal priceMaster = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, instrument.instrumentABBV, "last"));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionABBV.get(0).getQuantity().multiply(priceMaster)).add(positionAAPL.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity));
        //проверяем параметры позиции с расчетами
        checkPosition(positionABBV, priceMaster, slavePortfolioValue, slavePositionsValue, masterPositionRateABBV, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "0", nullValue());
        checkPosition(positionAAPL, price, slavePortfolioValue, slavePositionsValue, masterPositionRateAAPL, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", notNullValue());
        assertThat("ChangedAt позиции в портфеле slave не равен", positionAAPL.get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    private static Stream<Arguments> provideFlagNotChange() {
        return Stream.of(
            Arguments.of(true, true, true, true),
            Arguments.of(false, false, false, false),
            Arguments.of(false, true, false, true),
            Arguments.of(true, false, true, false)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideFlagNotChange")
    @AllureId("1439728")
    @Tags({@Tag("qa")})
    @DisplayName("C1439728.AnalyzePortfolio.Набор позиций slave-портфеля, позиции есть в slave_portfolio, но нет в master_portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1439728(Boolean buy, Boolean sell, Boolean buyRes, Boolean sellRes) {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3.0", date, 3, positionAction);
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
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", date, null, new BigDecimal("108.53"),
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(sellRes));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("688348")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C688348.AnalyzePortfolio.Анализ портфеля.Набор позиций slave-портфеля по облигациям")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C688348() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerALFAperp, instrument.classCodeALFAperp,
            "105", "100", "105");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Buy", "1", "1");
        //получаем данные для перерасчета бумаги типа облигация
        List<String> list = steps.getPriceFromExchangePositionCache(instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, SIEBEL_ID_MASTER);
        String aci = list.get(0);
        String nominal = list.get(1);
        String minPrIncrement = list.get(2);
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, "2.0", date, 3, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave
        String baseMoneySlave = "6657.23";
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
        BigDecimal getprice = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, instrument.instrumentALFAperp, "last"));
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
        await().atMost(TEN_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);

        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerALFAperp))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, "0", nullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1323457")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1323457.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio. Для валютных позиций USD")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1323457() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchangeFX("FX");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerUSDRUB, instrument.classCodeUSDRUB,
            "105.4975", "104.51", "106.475");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerSBER, instrument.classCodeSBER,
            "2668.25", "2460.67", "2445.48");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerUSDRUB, instrument.classCodeUSDRUB,
            "Sell", "39", "39");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "39", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceUSD = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, instrument.instrumentUSDRUB,"last" ));
        BigDecimal priceNewUSD = priceUSD.divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal newMinPriceIncrement = new BigDecimal(minPriceIncrement).divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal roundPriceNew = priceNewUSD.divide(newMinPriceIncrement, 0, RoundingMode.HALF_UP)
            .multiply(newMinPriceIncrement).stripTrailingZeros();
        BigDecimal priceSBER = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.instrumentSBER, "last"));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerSBER))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionUSD = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerUSDRUB))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionSBER.get(0).getQuantity().multiply(priceSBER)).add(positionUSD.get(0).getQuantity().multiply(priceNewUSD));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity().setScale(6), is(targetFeeReserveQuantity.setScale(6)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity().setScale(6), is(actualFeeReserveQuantity.setScale(6)));
        //проверяем параметры позиции с расчетами
        checkPosition(positionSBER, priceSBER, slavePortfolioValue, slavePositionsValue, masterPositionRateSBER, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "0", nullValue());
        checkPosition(positionUSD, roundPriceNew, slavePortfolioValue, slavePositionsValue, masterPositionRateUSD, instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "39", notNullValue());
        assertThat("Проверяем флаг buy_enabled", positionUSD.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionUSD.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1346546")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1346546.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio.Для валютных позиций GBP")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1346546() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchangeFX("FX");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerGBP, instrument.classCodeGBP,
            "140.9075", "138.195", "140.9075");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerSBER, instrument.classCodeSBER,
            "2668.25", "2460.67", "2445.48");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerUSDRUB, instrument.classCodeUSDRUB,
            "Sell", "39", "39");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //получаем идентификатор подписки
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerGBP,
            instrument.tradingClearingAccountGBP, "39", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceGBP = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerGBP,
            instrument.tradingClearingAccountGBP, instrument.instrumentGBP, "last"));
        BigDecimal priceNewGBP = priceGBP.divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal newMinPriceIncrement = new BigDecimal(minPriceIncrement).divide(new BigDecimal(currentNominal), 10, BigDecimal.ROUND_HALF_UP);
        BigDecimal roundPriceNew = priceNewGBP.divide(newMinPriceIncrement, 0, RoundingMode.HALF_UP)
            .multiply(newMinPriceIncrement).stripTrailingZeros();
        BigDecimal priceSBER = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.instrumentSBER, "last"));
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
        BigDecimal masterPositionRateGBP = new BigDecimal("0");
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionSBER = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerSBER))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionGBP = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerGBP))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionSBER.get(0).getQuantity().multiply(priceSBER)).add(positionGBP.get(0).getQuantity().multiply(priceNewGBP));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity().setScale(6), is(targetFeeReserveQuantity.setScale(6)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity().setScale(6), is(actualFeeReserveQuantity.setScale(6)));
        //проверяем параметры позиции с расчетами
        checkPosition(positionSBER, priceSBER, slavePortfolioValue, slavePositionsValue, masterPositionRateSBER, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "0", nullValue());
        checkPosition(positionGBP, roundPriceNew, slavePortfolioValue, slavePositionsValue, masterPositionRateGBP, instrument.tickerGBP,
            instrument.tradingClearingAccountGBP, "39", notNullValue());
        assertThat("Проверяем флаг buy_enabled", positionGBP.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionGBP.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1382257")
    @Tags({@Tag("qa")})
    @DisplayName("C1382257. Флаги buy_enabled и sell_enabled у позиций не заполнены (нет записи)")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1382257() {
        String SIEBEL_ID_SLAVE = stpSiebel.siebelIdAnalyzeSlaveOnlyBaseMoney;
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
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
    @Tags({@Tag("qa")})
    @DisplayName("C1439616.Флаги buy_enabled и sell_enabled у позиции, которой нет у мастера на инициализации портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1439616() {
        String SIEBEL_ID_SLAVE = stpSiebel.siebelIdAnalyzeSlaveMoneyAndAAPL;
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //формируем команду на актуализацию для slave
        //передаем  базовую валюту и позицию, которой нет у мастера
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            1234, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        checkComparedToMasterVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //сохраняем в списки значения по позициям в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1382266")
    @Tags({@Tag("qa")})
    @DisplayName("C1382266. Проставляем флаг buy_enabled = true для operation = 'ACTUALIZE' и action = 'ADJUST'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    void C1382266() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "6551.10", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 1,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,
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
            contractIdSlave, 3, time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1387788")
    @Tags({@Tag("qa")})
    @DisplayName("C1387788. Проставляем значение флагов на false событие operation = 'ACTUALIZE' и action = 'ADJUST' и завели 0")
    @Subfeature("Успешные сценарии")
    @Description("Получили событие с baseMoneyPosition = 0")
    void C1387788() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "6551.10", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 1,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLightAndWithSellAndBuy(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, true, true);
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
            contractIdSlave, 3, time, Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("1387789")
    @Tags({@Tag("qa")})
    @DisplayName("C1387789. Определяем buy_enabled = false с action = MONEY_SELL_TRADE и оба флага у позиции включены")
    @Subfeature("Успешные сценарии")
    @Description("Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version = slave_portfolio.compared_to_master_version. lots после округления < 0 " +
        "И buy_enabled = true")
    void C1387789() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", true, true, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", true, true, date);
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
            contractIdSlave, 3, steps.createPosInCommand(instrument.tickerUSDRUB,
                instrument.tradingClearingAccountUSDRUB, 5, Tracking.Portfolio.Action.MONEY_SELL_TRADE),
            time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1385944")
    @Tags({@Tag("qa")})
    @DisplayName("C1385944. Operation = 'ACTUALIZE'.Action =MONEY_SELL_TRADE. Master_portfolio.version > slave_portfolio.compared_to_master_version. lots после округления < 0")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version = slave_portfolio.compared_to_master_version. Позиция > 0, lots после округления < 0 " +
        "И buy_enabled = true")
    void C1385944() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "0", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", false, null, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", true, true, date);
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
            contractIdSlave, 3, steps.createPosInCommand(instrument.tickerUSDRUB,
                instrument.tradingClearingAccountUSDRUB, 5,
                Tracking.Portfolio.Action.MONEY_SELL_TRADE), time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        checkSlavePortfolioVersion(3);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1385944")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1385944. Не достаточно средств докупить бумагу")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'SECURITY_BUY_TRADE'. " +
        "Master_portfolio.version = slave_portfolio.compared_to_master_version.  lots после округления = 0 " +
        "И buy_enabled = true")
    void C1385945() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchangeFX("FX");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerGBP, instrument.classCodeGBP,
            "140.9075", "138.195", "140.9075");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerSBER, instrument.classCodeSBER,
            "2668.25", "2460.67", "2445.48");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", instrument.tickerGBP,
            instrument.tradingClearingAccountGBP, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "30", instrument.tickerGBP,
            instrument.tradingClearingAccountGBP, "10", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "0", false, null, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "0", true, true, date);
        String baseMoney = "0";
        List<SlavePortfolio.Position> positionListForSlave = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 1,
            baseMoney, date, positionListForSlave);
        String baseMoneySl = "9900.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 1,
            contractIdSlave, 3, steps.createPosInCommand(instrument.tickerSBER,
                instrument.tradingClearingAccountSBER, 10, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(1).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(1).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1676480")
    @Tags({@Tag("qa")})
    @DisplayName("C1676480.'ACTUALIZE'.SECURITY_BUY_TRADE. Портфель изменился с предыдущего анализа. " +
        "Запись в slave_order_2.compared_to_master_version.Знак изменений < 0")
    @Subfeature("Успешные сценарии")
    @Description("'ACTUALIZE'.SECURITY_BUY_TRADE. Портфель изменился с предыдущего анализа." +
        "Запись в slave_order_2.compared_to_master_version.Знак изменений < 0. " +
        " sell_enabled = true")
    void C1676480() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем несколько портфелей для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7093.1", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_SELL_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6767.9", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("108.4"),
            new BigDecimal("0.0"), new BigDecimal("0.076400"), new BigDecimal("4.127"), false, false);
        String baseMoneySl = "5855.6";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки на покупку AAPL в slaveOrder2
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 1, 1,
            0, instrument.classCodeAAPL, 2, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("108.4"), new BigDecimal("4"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 5422,
            contractIdSlave, 2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 4,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }

    @SneakyThrows
    @Test
    @AllureId("1676546")
    @Tags({@Tag("qa")})
    @DisplayName("C1676546.'ACTUALIZE'.SECURITY_SELL_TRADE. Портфель изменился с предыдущего анализа. " +
        "Запись в slave_order_2.compared_to_master_version.Знак изменений > 0")
    @Subfeature("Успешные сценарии")
    @Description("'ACTUALIZE'.SECURITY_SELL_TRADE.Портфель изменился с предыдущего анализа." +
        "Запись в slave_order_2.compared_to_master_version.Знак изменений > 0. " +
        " buy_enabled = true")
    void C1676546() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем несколько портфелей для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7093.1", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "8", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6225.9", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "10", date, null, new BigDecimal("108.4"),
            new BigDecimal("0.1562"), new BigDecimal("-0.07980"), new BigDecimal("-5.1087"), false, false);
        String baseMoneySl = "5855.6";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки на продажу AAPL в slaveOrder2
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 1, 1,
            1, instrument.classCodeAAPL, 2, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("108.4"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 63972,
            contractIdSlave, 2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_SELL_TRADE), time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("1676592")
    @Tags({@Tag("qa")})
    @DisplayName("C1676592.'ACTUALIZE'.SECURITY_BUY_TRADE. Портфель изменился с предыдущего анализа. " +
        "Запись в slave_order_2 не найдена. compared_to_master_version = slave_portfolio.compared_to_master_version.Знак изменений < 0")
    @Subfeature("Успешные сценарии")
    @Description("'ACTUALIZE'.SECURITY_BUY_TRADE. Портфель изменился с предыдущего анализа." +
        "Запись в slave_order_2 не найдена. compared_to_master_version = slave_portfolio.compared_to_master_version.Знак изменений < 0" +
        " sell_enabled = true")
    void C1676592() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем несколько портфелей для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7093.1", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_SELL_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6767.9", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "0", date, null, new BigDecimal("108.4"),
            new BigDecimal("0.0"), new BigDecimal("0.076400"), new BigDecimal("4.127"), false, false);
        String baseMoneySl = "5855.6";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 5422,
            contractIdSlave, 2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 4,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1676685")
    @Tags({@Tag("qa")})
    @DisplayName("C1676685.'ACTUALIZE'.SECURITY_SELL_TRADE. Портфель изменился с предыдущего анализа. " +
        "Запись в slave_order_2.compared_to_master_version = null.Знак изменений > 0")
    @Subfeature("Успешные сценарии")
    @Description("'ACTUALIZE'.SECURITY_SELL_TRADE.Портфель изменился с предыдущего анализа." +
        "Запись в slave_order_2.compared_to_master_version = null.Знак изменений > 0. " +
        " buy_enabled = true")
    void C1676685() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем несколько портфелей для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7093.1", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "8", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6225.9", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "10", date, null, new BigDecimal("108.4"),
            new BigDecimal("0.1562"), new BigDecimal("-0.07980"), new BigDecimal("-5.1087"), false, false);
        String baseMoneySl = "5855.6";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки на продажу AAPL в slaveOrder2
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 1, 1,
            1, instrument.classCodeAAPL, null, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("108.4"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 63972,
            contractIdSlave, 2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_SELL_TRADE), time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1676700")
    @Tags({@Tag("qa")})
    @DisplayName("C1676700.'ACTUALIZE'.SECURITY_SELL_TRADE. Портфель изменился с предыдущего анализа. " +
        "Запись в slave_order_2 в рамках другой стратегии.compared_to_master_version = slave_portfolio.compared_to_master_version." +
        "Знак изменений > 0")
    @Subfeature("Успешные сценарии")
    @Description("'ACTUALIZE'.SECURITY_SELL_TRADE.Портфель изменился с предыдущего анализа." +
        "Запись в slave_order_2.compared_to_master_version = null.Знак изменений > 0. " +
        " buy_enabled = true")
    void C1676700() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем несколько портфелей для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7093.1", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "8", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6225.9", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "10", date, null, new BigDecimal("108.4"),
            new BigDecimal("0.1562"), new BigDecimal("-0.07980"), new BigDecimal("-5.1087"), false, false);
        String baseMoneySl = "5855.6";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки на продажу AAPL в slaveOrder2 по другой стратегии
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, UUID.randomUUID(), 1, 1,
            1, instrument.classCodeAAPL, 5, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("108.4"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 63972,
            contractIdSlave, 2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_SELL_TRADE), time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1385949")
    @Tags({@Tag("qa")})
    @DisplayName("C1385949. Operation = 'ACTUALIZE'.Action =MONEY_SELL_TRADE. Master_portfolio.version > slave_portfolio.compared_to_master_version. Позиция < 0. lots после округления < 0")
    @Subfeature("Успешные сценарии")
    @Description(" Operation = 'ACTUALIZE'. ACTION = 'MONEY_SELL_TRADE'. " +
        "Master_portfolio.version > slave_portfolio.compared_to_master_version. Позиция < 0  lots после округления < 0 " +
        "И buy_enabled = true")
    void C1385949() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);

        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "5", null, false, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", false, false, date);
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
            contractIdSlave, 3, steps.createPosInCommand(instrument.tickerUSDRUB,
                instrument.tradingClearingAccountUSDRUB, 5, Tracking.Portfolio.Action.MONEY_SELL_TRADE),
            time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "9999.99", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "9999.99", masterPos);
        masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "10", instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "9999.99", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerUSDRUB,
            instrument.tradingClearingAccountUSDRUB, "10", true, true, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, "20", false, false, date);
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
            contractIdSlave, 3, steps.createPosInCommand(instrument.tickerUSDRUB,
                instrument.tradingClearingAccountUSDRUB, 0, Tracking.Portfolio.Action.MONEY_SELL_TRADE),
            time, Tracking.Portfolio.Action.MONEY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1677052")
    @Tags({@Tag("qa")})
    @DisplayName("C1677052.'ACTUALIZE'.SECURITY_BUY_TRADE. Портфель не изменился с предыдущего анализа. " +
        "Запись в slave_order_2.compared_to_master_version")
    @Subfeature("Успешные сценарии")
    @Description("'ACTUALIZE'.SECURITY_BUY_TRADE. Портфель изменился с предыдущего анализа." +
        "Запись в slave_order_2.compared_to_master_version.")
    void C1677052() {
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем несколько портфелей для master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7093.1", positionList);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.1", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //создаем портфель slave
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("108.4"),
            new BigDecimal("0.0"), new BigDecimal("0.076400"), new BigDecimal("4.127"), false, false);
        String baseMoneySl = "5855.6";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //создаем запись о выставлении заявки на покупку AAPL в slaveOrder2
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 1, 1,
            0, instrument.classCodeAAPL, 2, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("108.4"), new BigDecimal("4"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 5422,
            contractIdSlave, 2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 4,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoney = "0";
        List<SlavePortfolio.Position> createListSlaveOnePosOld = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null,
            new BigDecimal("108.53"), new BigDecimal("0"), new BigDecimal("0.0765"),
            new BigDecimal("0.0000"), false, false);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 0, 2,
            baseMoney, date, createListSlaveOnePosOld);
        //создаем портфель для ведомого
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0"), new BigDecimal("0.0760"), new BigDecimal("4.936"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 644555, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL,
                instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkSlavePortfolioVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6445.55"));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", notNullValue());
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 742164, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL,
                instrument.tradingClearingAccountAAPL, 6, Tracking.Portfolio.Action.SECURITY_SELL_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("7421.64"));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "6", notNullValue());
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(false));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(false));
    }


    private static Stream<Arguments> provideSecurityBuyTradeResultBuyTrue() {
        return Stream.of(
            Arguments.of(true, true, true, false),
            Arguments.of(true, false, true, false),
            Arguments.of(false, false, false, false),
            Arguments.of(false, true, false, false)
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
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        id = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoney = "0";
        List<SlavePortfolio.Position> createListSlaveOnePosOld = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("108.53"),
            new BigDecimal("0"), new BigDecimal("0.0765"), new BigDecimal("0.0000"), false, false);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 0, 2,
            baseMoney, date, createListSlaveOnePosOld);
        //создаем портфель для ведомого
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0"), new BigDecimal("0.0760"), new BigDecimal("4.936"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        slaveOrder2Dao.insertIntoSlaveOrder2WithFilledQuantity(contractIdSlave, strategyId, 1, 1,
            0, instrument.classCodeAAPL, new BigDecimal("0"), id, UUID.randomUUID(), new BigDecimal("110.15"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 677970, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                2, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 2), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantity = masterPortfolio.getPositions().get(0).getQuantity().multiply(price);
        BigDecimal masterPortfolioValue = masterPosQuantity.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRate = masterPosQuantity.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6779.70"));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(sellRes));
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2", notNullValue());
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        //создаем портфель для slave в cassandra
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        slaveOrder2Dao.insertIntoSlaveOrder2WithFilledQuantity(contractIdSlave, strategyId, 1, 1,
            1, instrument.classCodeAAPL, new BigDecimal("0"), id, UUID.randomUUID(), new BigDecimal("110.15"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 721124, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL,
                instrument.tradingClearingAccountAAPL, 8, Tracking.Portfolio.Action.SECURITY_SELL_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(2));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("7211.24"));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "8", notNullValue());
        assertThat("Проверяем флаг buy_enabled ", slavePortfolio.getPositions().get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", slavePortfolio.getPositions().get(0).getSellEnabled(), is(sellRes));
    }


    private static Stream<Arguments> provideSecurityBuyTradeResultBuy() {
        return Stream.of(
            Arguments.of(true, true, true, false),
            Arguments.of(false, false, true, false),
            Arguments.of(false, true, true, true),
            Arguments.of(true, false, true, false)
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        List<MasterPortfolio.Position> masterPosNew = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "5675.1", masterPosNew);
        List<MasterPortfolio.Position> masterPosLast = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "7", instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "5459.1", masterPosLast);
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoney = "0";
        List<SlavePortfolio.Position> createListSlaveOnePosOld = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("108.53"),
            new BigDecimal("0"), new BigDecimal("0.0765"), new BigDecimal("0.0000"), false, false);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 0, 2,
            baseMoney, date, createListSlaveOnePosOld);
        //создаем портфель для ведомого
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "0", date, null, new BigDecimal("107.78"),
            new BigDecimal("0"), new BigDecimal("0.0760"), new BigDecimal("4.936"), buy, sell);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 644555, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionABBV.get(0).getQuantity().multiply(priceABBV)).add(position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("6445.55"));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", notNullValue());
        //проверяем параметры позиции с расчетами
        checkPosition(positionABBV, priceABBV, slavePortfolioValue, slavePositionsValue, masterPositionRateABBV, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "0", nullValue());
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
            Arguments.of(true, false, true, true)
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPosOld = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPosOld);
        List<MasterPortfolio.Position> masterPosNew = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "5", instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "5675.1", masterPosNew);
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "3", instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "3", date, 4, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_SELL_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "5892.1", masterPos);
        //создаем портфель для slave в cassandra
        String baseMoneySlave = "7000";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "10", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), buy, sell);

        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        slaveOrder2Dao.insertIntoSlaveOrder2WithFilledQuantity(contractIdSlave, strategyId, 1, 1,
            1, instrument.classCodeAAPL, new BigDecimal("0"), id, UUID.randomUUID(), new BigDecimal("110.15"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //отправляем команду на актуализацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 721124, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL,
                instrument.tradingClearingAccountAAPL, 8, Tracking.Portfolio.Action.SECURITY_SELL_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_SELL_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionABBV.get(0).getQuantity().multiply(priceABBV)).add(position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(4));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("7211.24"));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity.setScale(4)));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity.setScale(4)));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "8", notNullValue());
        //проверяем параметры позиции с расчетами
        checkPosition(positionABBV, priceABBV, slavePortfolioValue, slavePositionsValue, masterPositionRateABBV, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "0", nullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(buyRes));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(sellRes));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1479051")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1479051.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio.SlavePortfolioValue <= 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1479051() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerABBV, instrument.classCodeABBV,
            "292", "289.4", "292");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Sell", "6", "6");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "7", instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "4", date, 3, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        String baseMoneySlave = "-3657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionOnePosWithEnable(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "6", date, null, new BigDecimal("107.78"),
            new BigDecimal("0.1342"), new BigDecimal("-0.0577"), new BigDecimal("-4.2986"), true, true);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal priceAAPL = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.instrumentAAPL, "last"));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, instrument.instrumentABBV, "last"));
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        Thread.sleep(5000);
        //получаем портфель slave
//        checkComparedToMasterVersion(3);
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());

        //выполняем расчеты
        BigDecimal masterPosQuantityAAPL = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceAAPL);
        BigDecimal masterPosQuantityABBV = masterPortfolio.getPositions().get(1).getQuantity().multiply(priceABBV);
        BigDecimal masterPortfolioValue = masterPosQuantityAAPL.add(masterPosQuantityABBV)
            .add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateAAPL = masterPosQuantityAAPL.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateABBV = masterPosQuantityABBV.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionABBV.get(0).getQuantity().multiply(priceABBV)).add(positionAAPL.get(0).getQuantity().multiply(priceAAPL));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        if (baseMoneyPositionQuantity.compareTo(BigDecimal.ZERO) < 0){
            baseMoneyPositionQuantity=BigDecimal.ZERO;
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add( slavePortfolio.getBaseMoneyPosition().getQuantity()).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(new BigDecimal("0")));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(new BigDecimal("0")));
        //проверяем параметры позиции с расчетами
        checkPosition(positionAAPL, priceAAPL, slavePortfolioValue, slavePositionsValue, masterPositionRateAAPL, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "6", notNullValue());
        BigDecimal slavePositionRate = (positionABBV.get(0).getQuantity().multiply(priceABBV)).divide(slavePositionsValue, 4, RoundingMode.HALF_UP);
        BigDecimal slavePositionRateDiff = BigDecimal.ZERO;
        BigDecimal slavePositionQuantityDiff = slavePositionRateDiff.multiply(slavePositionsValue)
            .divide(priceABBV, 4, BigDecimal.ROUND_HALF_UP);
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionABBV.get(0).getTicker(), is(instrument.tickerABBV));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна",
            positionABBV.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountABBV));
        assertThat("Quantity позиции в портфеле slave не равна", positionABBV.get(0).getQuantity().toString(), is("0"));
        assertThat("Price позиции в портфеле slave не равен", positionABBV.get(0).getPrice(), is(priceABBV));
        assertThat("Rate позиции в портфеле slave не равен", positionABBV.get(0).getRate().doubleValue(), is(slavePositionRate.doubleValue()));
        assertThat("RateDiff позиции в портфеле slave не равен", positionABBV.get(0).getRateDiff(), is(slavePositionRateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", positionABBV.get(0).getQuantityDiff(), is(slavePositionQuantityDiff));
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1481329")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1481329.AnalyzePortfolio.Набор позиций slave-портфеля, позиции в slave_portfolio и в master_portfolio.Стоимость портфеля <= 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1481329() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerABBV, instrument.classCodeABBV,
            "292", "289.4", "292");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Sell", "4", "4");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "7", instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "4", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "-1893.25";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "4", true, true, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "2", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal priceAAPL = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.instrumentAAPL, "last"));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, instrument.instrumentABBV, "last"));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantityAAPL = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceAAPL);
        BigDecimal masterPosQuantityABBV = masterPortfolio.getPositions().get(1).getQuantity().multiply(priceABBV);
        BigDecimal masterPortfolioValue = masterPosQuantityAAPL.add(masterPosQuantityABBV)
            .add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateAAPL = masterPosQuantityAAPL.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateABBV = masterPosQuantityABBV.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionABBV.get(0).getQuantity().multiply(priceABBV)).add(positionAAPL.get(0).getQuantity().multiply(priceAAPL));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;

        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        if (baseMoneyPositionQuantity.compareTo(BigDecimal.ZERO) < 0){
            baseMoneyPositionQuantity=BigDecimal.ZERO;
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add( slavePortfolio.getBaseMoneyPosition().getQuantity()).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(new BigDecimal("0")));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(new BigDecimal("0")));
        //проверяем параметры позиции с расчетами
        checkPosition(positionAAPL, priceAAPL, slavePortfolioValue, slavePositionsValue, masterPositionRateAAPL, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "4", notNullValue());
        checkPosition(positionABBV, priceABBV, slavePortfolioValue, slavePositionsValue, masterPositionRateABBV, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "2", notNullValue());
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1481368")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1481368.AnalyzePortfolio.Набор позиций slave-портфеля, позиции есть в slave_portfolio, но нет в master_portfolio.Стоимость портфеля <= 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1481368() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerABBV, instrument.classCodeABBV,
            "292", "289.4", "292");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerABBV, instrument.classCodeABBV,
            "Sell", "2", "2");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "-1893.25";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "4", true, true, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "2", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal priceAAPL = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.instrumentAAPL, "last"));
        BigDecimal priceABBV = new BigDecimal(steps.getPriceFromPriceCacheOrMD(instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, instrument.instrumentABBV, "last"));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantityAAPL = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceAAPL);
        BigDecimal masterPosQuantityABBV = BigDecimal.ZERO;
        BigDecimal masterPortfolioValue = masterPosQuantityAAPL.add(masterPosQuantityABBV)
            .add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateAAPL = masterPosQuantityAAPL.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal masterPositionRateABBV = masterPosQuantityABBV.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerABBV))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (positionABBV.get(0).getQuantity().multiply(priceABBV))
            .add(positionAAPL.get(0).getQuantity().multiply(priceAAPL));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;

        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        if (baseMoneyPositionQuantity.compareTo(BigDecimal.ZERO) < 0){
            baseMoneyPositionQuantity=BigDecimal.ZERO;
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add( slavePortfolio.getBaseMoneyPosition().getQuantity()).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(new BigDecimal("0")));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(new BigDecimal("0")));
        //проверяем параметры позиции с расчетами
        checkPosition(positionAAPL, priceAAPL, slavePortfolioValue, slavePositionsValue, masterPositionRateAAPL, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "4", notNullValue());
        checkPosition(positionABBV, priceABBV, slavePortfolioValue, slavePositionsValue, masterPositionRateABBV, instrument.tickerABBV,
            instrument.tradingClearingAccountABBV, "2", notNullValue());
        assertThat("Проверяем флаг buy_enabled", positionAAPL.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionAAPL.get(0).getSellEnabled(), is(true));
        assertThat("Проверяем флаг buy_enabled", positionABBV.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", positionABBV.get(0).getSellEnabled(), is(true));
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
    }


    @SneakyThrows
    @Test
    @AllureId("1481411")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1481411.AnalyzePortfolio.Пустой портфель slave.Стоимость портфеля <= 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1481411() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "-1893.25";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal priceAAPL = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantityAAPL = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceAAPL);
        BigDecimal masterPortfolioValue = masterPosQuantityAAPL.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateAAPL = masterPosQuantityAAPL.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = positionAAPL.get(0).getQuantity().multiply(priceAAPL);
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;

        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        if (baseMoneyPositionQuantity.compareTo(BigDecimal.ZERO) < 0){
            baseMoneyPositionQuantity=BigDecimal.ZERO;
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add( slavePortfolio.getBaseMoneyPosition().getQuantity()).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(new BigDecimal("0")));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(new BigDecimal("0")));
        BigDecimal slavePositionRateDiff = BigDecimal.ZERO;
        BigDecimal slavePositionRate = BigDecimal.ZERO;
        BigDecimal slavePositionQuantityDiff = slavePositionRateDiff.multiply(slavePositionsValue)
            .divide(priceAAPL, 4, BigDecimal.ROUND_HALF_UP);
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна",
            positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("0"));
        assertThat("Price позиции в портфеле slave не равен", positionAAPL.get(0).getPrice(), is(priceAAPL));
        assertThat("Rate позиции в портфеле slave не равен", positionAAPL.get(0).getRate().doubleValue(), is(slavePositionRate.doubleValue()));
        assertThat("RateDiff позиции в портфеле slave не равен", positionAAPL.get(0).getRateDiff(), is(slavePositionRateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", positionAAPL.get(0).getQuantityDiff(), is(slavePositionQuantityDiff));
    }


    @SneakyThrows
    @Test
    @AllureId("1481628")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1481628.AnalyzePortfolio.Пустой портфель slave.Стоимость портфеля = 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1481628() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Sell", "1", "1");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
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
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "2.0", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1154.4", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для ведомого
        String baseMoneySlave = "0";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        BigDecimal priceAAPL = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //выполняем расчеты
        BigDecimal masterPosQuantityAAPL = masterPortfolio.getPositions().get(0).getQuantity().multiply(priceAAPL);
        BigDecimal masterPortfolioValue = masterPosQuantityAAPL.add(masterPortfolio.getBaseMoneyPosition().getQuantity());
        BigDecimal masterPositionRateAAPL = masterPosQuantityAAPL.divide(masterPortfolioValue, 4, BigDecimal.ROUND_HALF_UP);
        //сохраняем в список значения по позиции в портфеле
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = BigDecimal.ZERO;
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;

        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        if (baseMoneyPositionQuantity.compareTo(BigDecimal.ZERO) < 0){
            baseMoneyPositionQuantity=BigDecimal.ZERO;
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);
        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add( slavePortfolio.getBaseMoneyPosition().getQuantity()).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(2));
        assertThat("ComparedToMasterVersion портфеля slave не равна", slavePortfolio.getComparedToMasterVersion(), is(3));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(new BigDecimal("0")));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(new BigDecimal("0")));
        BigDecimal slavePositionRateDiff = BigDecimal.ZERO;
        BigDecimal slavePositionRate = BigDecimal.ZERO;
        BigDecimal slavePositionQuantityDiff = slavePositionRateDiff.multiply(slavePositionsValue)
            .divide(priceAAPL, 4, BigDecimal.ROUND_HALF_UP);
        assertThat("ticker бумаги позиции в портфеле slave не равна", positionAAPL.get(0).getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна",
            positionAAPL.get(0).getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("Quantity позиции в портфеле slave не равна", positionAAPL.get(0).getQuantity().toString(), is("0"));
        assertThat("Price позиции в портфеле slave не равен", positionAAPL.get(0).getPrice(), is(priceAAPL));
        assertThat("Rate позиции в портфеле slave не равен", positionAAPL.get(0).getRate().doubleValue(), is(slavePositionRate.doubleValue()));
        assertThat("RateDiff позиции в портфеле slave не равен", positionAAPL.get(0).getRateDiff(), is(slavePositionRateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", positionAAPL.get(0).getQuantityDiff(), is(slavePositionQuantityDiff));
    }


    @SneakyThrows
    @Test
    @AllureId("1698354")
    @Tags({@Tag("qa"), @Tag("qa2")})
    @DisplayName("C1698354.AnalyzePortfolio.Набор позиций slave-портфеля, позиции нет в slave_portfolio." +
        "Минимальное значение targetFeeReserveQuantity")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию изменений виртуальных портфелей master'ов.")
    void C1698354() {
        mocksBasicSteps.createDataForMockAnalizeBrokerAccount(SIEBEL_ID_MASTER, SIEBEL_ID_SLAVE,
            stpMockSlaveDate.investIdMasterAnalyze,  stpMockSlaveDate.investIdSlaveAnalyze,
            stpMockSlaveDate.contractIdMasterAnalyze,  stpMockSlaveDate.contractIdSlaveAnalyze);
        mocksBasicSteps.createDataForMockAnalizeShedulesExchange("SPB_MORNING_WEEKEND");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(instrument.tickerAAPL, instrument.classCodeAAPL,
            "108.22", "109.22", "107.22");
        mocksBasicSteps.createDataForMockAnalizeMdPrices(stpMockSlaveDate.contractIdSlaveAnalyze,
            stpMockSlaveDate.clientCodeSlaveAnalyze, instrument.tickerAAPL, instrument.classCodeAAPL,
            "Sell", "1", "1");
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
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked, null,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        // создаем портфель мастера с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build()).build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "108", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "259.17", masterPos);
        //получаем идентификатор подписки
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        subscriptionId = subscription.getId();
        //создаем портфель для slave в cassandra c пустой позицией по бумаге
        //создаем портфель для ведомого
        String baseMoneySlave = "100";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "50", date, 1, new BigDecimal("107"),
            new BigDecimal("0.981700"), new BigDecimal("-0.003600"), new BigDecimal("-0.1779"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        createCommandSynTrackingSlaveCommand(contractIdSlave, time);
        //получаем значение price из кеша exchangePositionPriceCache
        BigDecimal price = new BigDecimal(steps.getPriceFromExchangePositionPriceCacheWithSiebel(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID_SLAVE));
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
            .filter(ps -> ps.getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());
        BigDecimal slavePositionsValue = (position.get(0).getQuantity().multiply(price));
        BigDecimal baseMoneyPositionQuantity = slavePortfolio.getBaseMoneyPosition().getQuantity();
        BigDecimal slavePortfolioTotal = slavePositionsValue.add(baseMoneyPositionQuantity);
        //определяем резерв под списание комиссии:
        BigDecimal targetFeeReserveQuantity = BigDecimal.ZERO;
        if (slavePortfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            targetFeeReserveQuantity = (slavePositionsValue.add(baseMoneyPositionQuantity)).multiply(targetFeeReserveRate);
        }
        //считаем фактическое значение резерва actualFeeReserveQuantity
        BigDecimal actualFeeReserveQuantity = min(targetFeeReserveQuantity, baseMoneyPositionQuantity);

        //рассчитываем общую стоимость slave-портфеля slavePortfolioValue
        BigDecimal slavePortfolioValue = slavePositionsValue.add(baseMoneyPositionQuantity).subtract(actualFeeReserveQuantity);
        //проверяем расчеты и содержимое позиции slave
        assertThat("Версия портфеля slave не равна", slavePortfolio.getVersion(), is(1));
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        assertThat("Время changed_at для slave_position не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(utc.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("целевое значение резерва не равна", slavePortfolio.getTargetFeeReserveQuantity(), is(targetFeeReserveQuantity));
        assertThat("фактическое значение резерва не равна", slavePortfolio.getActualFeeReserveQuantity(), is(actualFeeReserveQuantity));
        //проверяем параметры позиции с расчетами
        checkPosition(position, price, slavePortfolioValue, slavePositionsValue, masterPositionRate, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, "50", notNullValue());
        assertThat("Проверяем флаг buy_enabled", position.get(0).getBuyEnabled(), is(true));
        assertThat("Проверяем флаг sell_enabled", position.get(0).getSellEnabled(), is(true));
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
                              BigDecimal slavePositionsValue, BigDecimal masterPositionRate, String ticker, String tradingClearingAccount,
                              String quantity, Matcher<Object> changedAt) {
        BigDecimal slavePositionRate = BigDecimal.ZERO;
        BigDecimal slavePositionRateDiff = BigDecimal.ZERO;
        BigDecimal slavePositionQuantityDiff = BigDecimal.ZERO;
        if (slavePortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            slavePositionRate = (position.get(0).getQuantity().multiply(price)).divide(slavePortfolioValue, 4, RoundingMode.HALF_UP);
            slavePositionRateDiff = masterPositionRate.subtract(slavePositionRate);
            slavePositionQuantityDiff = slavePositionRateDiff.multiply(slavePortfolioValue)
                .divide(price, 4, BigDecimal.ROUND_HALF_UP);
        }


        if (slavePortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            slavePositionRate = (position.get(0).getQuantity().multiply(price)).divide(slavePositionsValue, 4, RoundingMode.HALF_UP);

            slavePositionRateDiff = slavePositionRate.negate();
            slavePositionQuantityDiff = slavePositionRateDiff.multiply(slavePositionsValue)
                .divide(price, 4, BigDecimal.ROUND_HALF_UP);
        }
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
            Thread.sleep(3000);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }

    // ожидаем версию портфеля slave
    void checkSlavePortfolioVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(3000);
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, version);
            if (slavePortfolio.getVersion() == version) {
                i = 5;
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
