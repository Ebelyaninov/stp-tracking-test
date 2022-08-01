package socialTrackingCorpAction;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Step;
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
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.DividentDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.mocks.steps.fireg.GetDividendsSteps;
import ru.qa.tinkoff.mocks.steps.investmentAccount.MockInvestmentAccountSteps;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMockSlaveDateConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.*;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.invest.tracking.corpaction.TrackingCorpAction;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;

@Slf4j
@Epic("HandleCorpActionCommandTest - Обработка команд на обработку КД")
@Subfeature("Альтернативные сценарии")
@DisplayName("social-tracking-corp-action")
@Tags({@Tag("social-tracking-corp-action"), @Tag("HandleCorpActionCommandTest")})
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
    MocksBasicStepsConfiguration.class,
    StpTrackingMockSlaveDateConfiguration.class
})
public class HandleCorpActionCommandErrorTest {
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
    StpTrackingMasterSteps steps;
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpInstrument instrument;
    @Autowired
    CorpActionService corpActionService;
    @Autowired
    DividendService dividendService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    DividentDao dividentDao;
    @Autowired
    GetDividendsSteps getDividendsSteps;
    @Autowired
    MockInvestmentAccountSteps mockInvestmentAccountSteps;


    CorpAction corpAction;
    String contractIdMaster;
    int version;
    String siebelIdMaster;
    String title;
    String description;
    UUID strategyId;
    UUID investIdMaster;
    String dividendNetXS0191754729 = "3.375";
    String dividendNetAAPL = "0.22";
    String dividendIdAAPL = "486669";
    String paymentDate;
    String lastBuyDate;
    final String tickerNotFound = "TESTTEST";
    final UUID notFoundPositionId = UUID.fromString("d47e8733-1c1b-1e5b-1ad0-d1f9fd4ed4a2");


    @BeforeAll
    void getdataFromInvestmentAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
        //Создаем мок
//        createDataForMockRestAccount("3c28450a-766a-458c-b0ad-62a1d56adff8", siebelIdMaster, "2000058046");
        int randomNumber = 0 + (int) (Math.random() * 100);
        title = "Autotest " + String.valueOf(randomNumber);
        description = "autotest handleCorpActionCommand for Master";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        LocalDate localDateNow = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        paymentDate = localDateNow + "T03:00:00+03:00";
        lastBuyDate = localDateNow.minusDays(14).format(formatter) + "T03:00:00+03:00";
        //getDividendsSteps.clearGetDevidends();
//        createMockForAAPL();
//        createMockForGetDividendsWithOneItems(tickerNotFound, instrument.classCodeXS0191754729, "228", "191121",
//            dividendNetXS0191754729, "usd", paymentDate, lastBuyDate, "READY");
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
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
                corpActionService.deleteAllCoarpActionByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                dividendService.deleteAllDividendsByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                dividentDao.deleteAllDividendByContractAndStrategyId(contractIdMaster, strategyId);
            } catch (Exception e){
            }

        });
    }

    @SneakyThrows
    @Test
    @Tag("qa2")
    @AllureId("1866162")
    @DisplayName("C1866162.HandleCorpActionCommand. cut != '00:00:00'")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866162() {
        strategyId = UUID.fromString("d47e8766-4c4b-4e5b-8ad0-d5f9fd4ed4a1");
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(4).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "10", positionAction, version, version,
            baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "20", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем запись с lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version +2, version +2,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofSeconds(3));
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли запись в dividend", dividendList.size(), is(0));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку событий
        assertThat("Нашли событие в топике", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @Tag("qa2")
    @AllureId("1866183")
    @DisplayName("C1866183.HandleCorpActionCommand.  Валюта по дивиденду не соответствует валюте стратегии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866183() {
        strategyId = UUID.fromString("d47e8766-4c4b-4e5b-8ad0-d5f9fd4ed4a1");
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "10", positionAction, version, version,
            baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "20", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем запись с lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version +2, version +2,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofSeconds(5)).pollInterval(Duration.ofNanos(500)).until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку событий
        assertThat("Нашли событие в топике", messages.size(), is(0));
    }



    private static Stream<Arguments> provideParametresForStrategyStatus() {
        return Stream.of(
            Arguments.of(false),
            Arguments.of(true)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @Tag("qa2")
    @MethodSource("provideParametresForStrategyStatus")
    @AllureId("1866162")
    @DisplayName("C1866165.HandleCorpActionCommand. Не нашли запись в таблице strategy")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866165(Boolean notFoundStrategy) {
        strategyId = UUID.fromString("d47e8766-4c4b-4e5b-8ad0-d5f9fd4ed4a1");
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        clientService.createClient(investIdMaster, ClientStatusType.registered, null, ClientRiskProfile.aggressive);
        // создаем запись о договоре клиента в tracking.contract
        Contract contractMaster = new Contract()
            .setId(contractIdMaster)
            .setClientId(investIdMaster)
            .setState(ContractState.untracked)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        if (notFoundStrategy.equals(false)) {
            Map<String, BigDecimal> feeRateProperties = new HashMap<>();
            feeRateProperties.put("result", new BigDecimal("0.2"));
            feeRateProperties.put("management", new BigDecimal("0.04"));
            List<TestsStrategy> testsStrategiesList = new ArrayList<>();
            testsStrategiesList.add(new TestsStrategy());
            Strategy strategyMaster = new Strategy()
                .setId(strategyId)
                .setContract(contractMaster)
                .setTitle(title)
                .setBaseCurrency(StrategyCurrency.usd)
                .setRiskProfile(StrategyRiskProfile.aggressive)
                .setDescription(description)
                .setStatus(StrategyStatus.draft)
                .setSlavesCount(0)
                .setActivationTime(null)
                .setScore(1)
                .setFeeRate(feeRateProperties)
                .setOverloaded(false)
                .setTestsStrategy(testsStrategiesList)
                .setBuyEnabled(true)
                .setSellEnabled(true)
                .setBuyEnabled(true)
                .setSellEnabled(true);
            trackingService.saveStrategy(strategyMaster);
        }

        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofSeconds(3));
        //Проверяем записи в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли запись в dividend", dividendList.size(), is(0));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку событий
        assertThat("Нашли событие в топике", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @Tag("qa2")
    @AllureId("1866170")
    @DisplayName("C1866183.HandleCorpActionCommand.  Не нашли позицию в кэше exchangePositionCache")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866170() {
        strategyId = UUID.fromString("d47e8766-4c4b-4e5b-8ad0-d5f9fd4ed4a1");
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        createMasterPortfolioWithPosition(tickerNotFound, instrument.tradingClearingAccountXS0191754729, notFoundPositionId, "10", positionAction, version, version,
            baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        createMasterPortfolioWithPosition(tickerNotFound, instrument.tradingClearingAccountXS0191754729, notFoundPositionId, "20", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем запись с lastBuyDate
        createMasterPortfolioWithPosition(tickerNotFound, instrument.tradingClearingAccountXS0191754729, notFoundPositionId, "100", positionAction, version +2, version +2,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofSeconds(5)).pollInterval(Duration.ofNanos(500)).until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
//        checkdDividend(dividendList, dividendIdAAPL);
        assertThat("Кол-во двидендов != 0", dividendList.size(), is(0));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку событий
        assertThat("Нашли событие в топике только 1", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @Tag("qa2")
    @AllureId("1866167")
    @DisplayName("C1866167.HandleCorpActionCommand. Не нашли портфель мастера")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866167() {
        strategyId = UUID.fromString("d47e8766-4c4b-4e5b-8ad0-d5f9fd4ed4a1");
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией

        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofSeconds(3));
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли запись в dividend", dividendList.size(), is(0));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку событий
        assertThat("Нашли событие в топике", messages.size(), is(0));
    }


    @Step("Создаем мок, для ticker = {ticker} и classCode = {classCode}")
    void createMockForGetDividendsWithOneItems (String ticker, String classCode, String dividendId, String instrumentId, String dividendNet, String dividendCurrency, String paymentDate, String lastBuyDate, String status){
        getDividendsSteps.createGetDividends(getDividendsSteps.createBodyForGetDividendWithOneElement(ticker, classCode, dividendId, instrumentId,
            dividendNet, dividendCurrency, paymentDate, lastBuyDate, status));
    }

    void checkdDividend(List<Dividend> dividendList, String dividendId){
        Dividend getdividend = dividendList.stream()
            .filter(id -> id.getId().equals(Long.valueOf(dividendId)))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Не нашли дивиденд"));
        assertThat("dividendId != " + dividendId, getdividend.getId(), is(Long.valueOf(dividendId)));
        assertThat("strategyId != " + strategyId, getdividend.getStrategyId(), is(strategyId));
    }


    @Step("формируем команду на бработку совершенных корпоративных действий")
    public TrackingCorpAction.ActivateCorpActionCommand createActivateCorpActionCommand (OffsetDateTime now, OffsetDateTime cut) {

        TrackingCorpAction.ActivateCorpActionCommand.Dividend dividend =
            TrackingCorpAction.ActivateCorpActionCommand.Dividend.newBuilder().build();
        TrackingCorpAction.ActivateCorpActionCommand command = TrackingCorpAction.ActivateCorpActionCommand.newBuilder()
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setStrategyId(byteString(strategyId))
            .setCut(Timestamp.newBuilder()
                .setSeconds(cut.toEpochSecond())
                .build())
            .setDividend(dividend).build();

        return command;
    }

    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }

    @Step("Создание портфеля ведущего MasterPortfolio:  ")
        //создаем портфель master в cassandra с позицией
    void createMasterPortfolioWithPosition(String ticker, String tradingClearingAccount, UUID positionId, String quantityPos,
                                           Tracking.Portfolio.Position position,
                                           int versionPos, int version, String money, Date date) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .positionId(positionId)
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
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }

    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }

    @Step("Создаем мок, для ответа метода account/public/v1/invest/siebel")
    void createDataForMockRestAccount (String investIdMaster, String siebelIdMaster, String contractIdMaster){
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster, siebelIdMaster, contractIdMaster));
    }

    @Step("Создаем мок, для AAPL с 3 items")
    void createMockForAAPL (){
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        //Для pay-dividend-processing-days = 7d
        //String paymentDate = date.minusDays(6).format(formatter) + "T03:00:00+03:00";
        //Для pay-dividend-processing-days = 1d
        String paymentDate = date.format(formatter) + "T03:00:00+03:00";
        String lastBuyDate = date.minusDays(14).format(formatter) + "T03:00:00+03:00";
        getDividendsSteps.createGetDividends(getDividendsSteps.createBodyForAAPL(dividendNetAAPL, paymentDate, lastBuyDate));
    }
}
