package socialTrackingCorpAction;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.DividentDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.mocks.steps.fireg.GetDividendsSteps;
import ru.qa.tinkoff.mocks.steps.investmentAccount.MockInvestmentAccountSteps;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMockSlaveDateConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.CorpAction;
import ru.qa.tinkoff.tracking.entities.Dividend;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.invest.tracking.corpaction.TrackingCorpAction;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;


@Slf4j
@Epic("HandleCorpActionCommandTest - Обработка команд на обработку КД")

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
public class HandleCorpActionCommandTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
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
    String typeDVCA = "DVCA";
    String dividendTaxRate = "0.13";
    String dividendNetAAPL = "0.22";
    String dividendNetNOK = "0.0375";
    String dividendNetABBV = "0.00001";
    String dividendNetSBER = "18.7";
    String dividendNetXS0191754729 = "3.375";
    String dividendNetNMR = "10.229";
    String dividendIdAAPL = "486669";
    String dividendIdNOK = "3433";
    String dividendIdSBER = "525076";
    String dividendIdABBV = "524362";
    String dividendIdXS0191754729 = "11113";
    String dividendIdNMR = "228";
    String paymentDate;
    String lastBuyDate;
    String lastBuyDateMinus65Days;


    @BeforeAll
    void getdataFromInvestmentAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
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
        String paymentDatePlusDay = localDateNow.plusDays(1) + "T03:00:00+03:00";
        String paymentDatePlusTwoDays = localDateNow.plusDays(2) + "T03:00:00+03:00";
        String paymentDateMinusSevenDays = localDateNow.minusDays(7) + "T03:00:00+03:00";
        lastBuyDate = localDateNow.minusDays(14).format(formatter) + "T03:00:00+03:00";
        lastBuyDateMinus65Days = localDateNow.minusDays(65).format(formatter) + "T03:00:00+03:00";
//        getDividendsSteps.clearGetDevidends();
//        createMockForAAPL();
//        createMockForGetDividendsWithOneItems(instrument.tickerNOK, instrument.classCodeNOK, dividendIdNOK, "1911",
//            dividendNetNOK, "usd", paymentDate, lastBuyDate, "READY");
//        createMockForGetDividendsWithOneItems(instrument.tickerSBER, instrument.classCodeSBER, dividendIdSBER, dividendIdNOK,
//            dividendNetSBER, "rub", paymentDate, lastBuyDate, "READY");
//        createMockForGetDividendsWithOneItems(instrument.tickerSTM, instrument.classCodeSTM, "524433", "9309",
//            "0.06", "usd", paymentDatePlusTwoDays, lastBuyDate, "READY");
//        createMockForGetDividendsWithOneItems(instrument.tickerFB, instrument.classCodeFB, "524612", "2181",
//            "0.22", "usd", paymentDatePlusDay, lastBuyDate, "READY");
        //Для pay-dividend-processing-days = 7d
//        createMockForGetDividendsWithOneItems(instrument.tickerLNT, instrument.classCodeLNT, "479179", "2111",
//            "0.4275", "usd", paymentDateMinusSevenDays, lastBuyDate, "READY");
        //Для pay-dividend-processing-days = 1d
        String paymentDateMinusDay = localDateNow.minusDays(1) + "T03:00:00+03:00";
//        createMockForGetDividendsWithOneItems(instrument.tickerLNT, instrument.classCodeLNT, "479179", "2111",
//            "0.4275", "usd", paymentDateMinusDay, lastBuyDate, "READY");
//        createMockForGetDividendsWithOneItems(instrument.tickerABBV, instrument.classCodeABBV, dividendIdABBV, "2268",
//            dividendNetABBV, "usd", paymentDate, lastBuyDate, "READY");
//        createMockForGetDividendsWithOneItems(instrument.tickerXS0191754729, instrument.classCodeXS0191754729, dividendIdXS0191754729, "2017",
//            dividendNetXS0191754729, "usd", paymentDate, lastBuyDate, "READY");
//        createMockForGetDividendsWithOneItems(instrument.tickerNMR, instrument.classCodeNMR, dividendIdNMR, "822",
//            dividendNetNMR, "usd", paymentDate, lastBuyDateMinus65Days, "READY");
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
           try {
                trackingService.deleteStrategy(steps.strategyMaster);
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
    @AllureId("1865779")
    @DisplayName("C1865779.HandleCorpActionCommand.Успешная обработка команды на обработку КД")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1865779() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId = ", strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
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
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC).minusDays(11);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerSTM, instrument.tradingClearingAccountSTM, instrument.positionIdSTM, quantityPos, positionAction, version, version,
            baseMoneyPortfolio, date);
        //формируем команду на  обработку совершенных корпоративных действий
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
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
       // проверяем запись в таблице corp_action
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        assertThat("strategy_id не равен", corpAction.getStrategyId(), is(strategyId));
        assertThat("cut из входной команды не равен", corpAction.getCut().toLocalDate(), is(cut.toLocalDate().plusDays(1)));
        assertThat("type не равно", corpAction.getType(), is(typeDVCA));
    }


    @SneakyThrows
    @Test
    @AllureId("1865780")
    @DisplayName("C1865780.HandleCorpActionCommand. type - неизвестное значение")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1865780() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId :  {}", strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
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
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        Date date = Date.from(utc.toInstant());
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version, version,
            baseMoneyPortfolio, date);
        //формируем команду на  обработку совершенных корпоративных действий
        TrackingCorpAction.ActivateCorpActionCommand.Test dividendTest =
            TrackingCorpAction.ActivateCorpActionCommand.Test.newBuilder().build();
        TrackingCorpAction.ActivateCorpActionCommand command = TrackingCorpAction.ActivateCorpActionCommand.newBuilder()
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setStrategyId(byteString(strategyId))
            .setCut(Timestamp.newBuilder()
                .setSeconds(cut.toEpochSecond())
                .build())
            .setTest(dividendTest).build();
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        // проверяем запись в таблице corp_action
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(3)).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        assertThat("запись по стратегии не равно", corpActionOpt.isPresent(), is(false));
    }

    //ToDo часть теста, с проверкой данных в касандре не работает на qa2, из-за сервиса master, его отключили
    @SneakyThrows
    @Test
    @AllureId("1873898")
    @Tag("qa")
    @DisplayName("C1873898.HandleCorpActionCommand. Выгрузка 2 дивидендов с обработкой в master")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1873898() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= ", strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"10", positionAction, version, version,
            baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerNOK, instrument.tradingClearingAccountNOK, instrument.positionIdNOK,"20", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(1).toInstant()));

        List<String> listOfTickers = new ArrayList<>();
        listOfTickers.add(instrument.tickerAAPL);
        listOfTickers.add(instrument.tickerNOK);
        listOfTickers.add(instrument.tickerLNT);
        listOfTickers.add(instrument.tickerFB);
        listOfTickers.add(instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = new ArrayList<>();
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountAAPL);
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountNOK);
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountLNT);
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountFB);
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = new ArrayList<>();
        listOfPositionIds.add(instrument.positionIdAAPL);
        listOfPositionIds.add(instrument.positionIdNOK);
        listOfPositionIds.add(instrument.positionIdLNT);
        listOfPositionIds.add(instrument.positionIdFB);
        listOfPositionIds.add(instrument.positionIdSTM);
        List<String> listOfQty = new ArrayList<>();
        listOfQty.add("30");
        listOfQty.add("35");
        listOfQty.add("100");
        listOfQty.add("200");
        listOfQty.add("300");
        listOfQty.add("400");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
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
        await().atMost(FIVE_SECONDS).pollDelay(TWO_HUNDRED_MILLISECONDS).pollInterval(Duration.ofNanos(200)).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        //to list или проверить size
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем отправку 2 событий
        assertThat("Нашли не 2 события в топике", messages.size(), is(2));
        String key = message.getKey();
        //Проверяем событие с stp-tracking-master-command
        checkPortfolioCommand(portfolioCommand, key, "35", dividendNetNOK, dividendIdNOK, Tracking.Currency.USD, "NOK", "L01+00000SPB");
        //Получаем факт обработки дивиденда
        //Обработка в мастере
//        List<ru.qa.tinkoff.investTracking.entities.Dividend> getListOfDividends = dividentDao.findAllDividend(contractIdMaster, strategyId);
//        ru.qa.tinkoff.investTracking.entities.Dividend getDividendNoK = getListOfDividends.stream()
//            .filter(ticker -> ticker.getContext().getExchangePositionId().getTicker().equals("NOK"))
//            .collect(Collectors.toList()).get(0);
//        ru.qa.tinkoff.investTracking.entities.Dividend getDividendAAPL = getListOfDividends.stream()
//            .filter(ticker -> ticker.getContext().getExchangePositionId().getTicker().equals("AAPL"))
//            .collect(Collectors.toList()).get(0);
//        int versionForAAPL = getDividendAAPL.getContext().getVersion();
//        int versionForNOK = getDividendNoK.getContext().getVersion();
//        //Проверяем выгрузку дивиденда
//        BigDecimal dividendAmountAAPL = calculateAmount("30", dividendNetAAPL);
//        BigDecimal dividendAmountNok = calculateAmount("35", dividendNetNOK);
//        checkDividend(portfolioCommand, getDividendNoK, dividendAmountNok);
//        //checkDividend(portfolioCommand, getDividendAAPL, dividendAmountAAPL);
//        //Увеличиваем baseMoney на величену дивиденда
//        BigDecimal newBasemoneyPositionForNOK;
//        BigDecimal newBasemoneyPositionForAAPL;
//        //Проверяем порядок обработки дивиденда
//        if (versionForAAPL > versionForNOK) {
//            newBasemoneyPositionForNOK = new BigDecimal(baseMoneyPortfolio).add(dividendAmountNok);
//            newBasemoneyPositionForAAPL = newBasemoneyPositionForNOK.add(dividendAmountAAPL);
//        }
//        else {
//            newBasemoneyPositionForAAPL = new BigDecimal(baseMoneyPortfolio).add(dividendAmountAAPL);
//            newBasemoneyPositionForNOK = newBasemoneyPositionForAAPL.add(dividendAmountNok);
//        }
//        //Получаем нужную версию портфеля
//        List<MasterPortfolio> masterPortfolios = masterPortfolioDao.getAllMasterPortfolio(contractIdMaster, strategyId);
//        MasterPortfolio masterPortfolioForNok = masterPortfolios.stream()
//            .filter(version -> version.getVersion().equals(versionForNOK))
//            .collect(Collectors.toList()).get(0);
//        MasterPortfolio masterPortfolioForAAPL = masterPortfolios.stream()
//            .filter(version -> version.getVersion().equals(versionForAAPL))
//            .collect(Collectors.toList()).get(0);
//        //Проверяем увеличение базовой валюты
//        checkMasterPortfolio(masterPortfolioForNok,  newBasemoneyPositionForNOK);
//        checkMasterPortfolio(masterPortfolioForAAPL,  newBasemoneyPositionForAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("1892424")
    @Tag("qa2")
    @DisplayName("C1892424. Отфильтровываем инструмент из exchangePositionCache, если type NOT IN (etf, share)")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1892424() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем   портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        createMasterPortfolioWithPosition(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, "10", positionAction, version, version,
            baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerNOK, instrument.tradingClearingAccountNOK, instrument.positionIdNOK,"20", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(1).toInstant()));

        List<String> listOfTickers = new ArrayList<>();
        listOfTickers.add(instrument.tickerXS0191754729);
        listOfTickers.add(instrument.tickerNOK);

        List<String> listOfTradingClearAcconts = new ArrayList<>();
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountXS0191754729);
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountNOK);

        List<UUID> listOfPositionIds = new ArrayList<>();
        listOfPositionIds.add(instrument.positionIdXS0191754729);
        listOfPositionIds.add(instrument.positionIdNOK);

        List<String> listOfQty = new ArrayList<>();
        listOfQty.add("30");
        listOfQty.add("35");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, "100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729, instrument.positionIdXS0191754729, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
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
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли больше 1 записи в dividend", dividendList.size(), is(1));
        checkdDividend(dividendList, dividendIdNOK);

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        //to list или проверить size
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем отправку 2 событий
        assertThat("Нашли не 1 событие в топике", messages.size(), is(1));
        String key = message.getKey();
        //Проверяем событие с stp-tracking-master-command
        checkPortfolioCommand(portfolioCommand, key, "35", dividendNetNOK, dividendIdNOK, Tracking.Currency.USD, "NOK", instrument.tradingClearingAccountNOK);
    }


    @SneakyThrows
    @Test
    @AllureId("1866174")
    @Tag("qa2")
    @DisplayName("C1866174.HandleCorpActionCommand. Исключаем инструмент из массива dividend, если dividend.payment_date НЕ входит в intervalForPayment")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866174() {
        strategyId = UUID.randomUUID();
        log.info("Genereted strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerAAPL, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountAAPL, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdAAPL, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(4).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        //Добавляем запись которую будем использовать в расчётах
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
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
        await().atMost(FIVE_SECONDS).pollDelay(ONE_SECOND).ignoreExceptions().pollInterval(TWO_HUNDRED_MILLISECONDS).until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем отправку 2 событий
        assertThat("Нашли не 2 события в топике", messages.size(), is(2));
        String key = message.getKey();
        //Проверяем событие с stp-tracking-master-command
        if (portfolioCommand.getDividend().getExchangePositionId().getTicker().equals(instrument.tickerNOK)) {
            checkPortfolioCommand(portfolioCommand, key, "35", dividendNetNOK, dividendIdNOK, Tracking.Currency.USD, instrument.tickerNOK, instrument.tradingClearingAccountNOK);
        }
        else {
            checkPortfolioCommand(portfolioCommand, key, "30", dividendNetAAPL, dividendIdAAPL, Tracking.Currency.USD, instrument.tickerAAPL, instrument.tradingClearingAccountNOK);
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1866182")
    @Tag("qa2")
    @DisplayName("C1866182.HandleCorpActionCommand. Кол-во инструмента по дивиденду <= 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866182() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= ", strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerAAPL, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountAAPL, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdAAPL, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(4).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        //Добавляем запись которую будем использовать в расчётах
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, createQtyList("0", "-30", "100", "200", "300")
            , version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
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
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку 2 событий
        assertThat("Нашли событиt в топике", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1866182")
    @Tag("qa2")
    @DisplayName("C1866175.HandleCorpActionCommand. Нашли уже обработанный дивиденд")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866175() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerAAPL, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountAAPL, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdAAPL, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(4).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        //Добавляем запись которую будем использовать в расчётах
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
        //Добавляем запись в dividend
        dividendService.insertIntoDividend(Long.valueOf(dividendIdNOK), strategyId, java.sql.Timestamp.from(now.toInstant()));
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
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем отправку одного событий
        assertThat("Нашли не одно событие в топике", messages.size(), is(1));
        String key = message.getKey();
        //Проверяем событие с stp-tracking-master-command
        checkPortfolioCommand(portfolioCommand, key, "30", dividendNetAAPL, dividendIdAAPL, Tracking.Currency.USD, "AAPL", "TKCBM_TCAB");
    }


    @SneakyThrows
    @Test
    @AllureId("1866180")
    @Tag("qa2")
    @DisplayName("C1866180.HandleCorpActionCommand. Не нашли позицию в портфеле мастера за период portfolio.changed_at (по МСК, округленный до даты) = дата last_buy_date")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866180() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " +  strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerAAPL, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountAAPL, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdAAPL, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(4).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));

        List<String> listOfTickersForCount = new ArrayList<>();
        listOfTickersForCount.add(instrument.tickerAAPL);
        listOfTickersForCount.add(instrument.tickerLNT);
        listOfTickersForCount.add(instrument.tickerFB);
        listOfTickersForCount.add(instrument.tickerSTM);
        List<String> listOfTradingClearAccontsForCount = new ArrayList<>();
        listOfTradingClearAccontsForCount.add(instrument.tradingClearingAccountAAPL);
        listOfTradingClearAccontsForCount.add(instrument.tradingClearingAccountLNT);
        listOfTradingClearAccontsForCount.add(instrument.tradingClearingAccountFB);
        listOfTradingClearAccontsForCount.add(instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIdForCount = new ArrayList<>();
        listOfPositionIdForCount.add(null);
        listOfPositionIdForCount.add(instrument.positionIdLNT);
        listOfPositionIdForCount.add(instrument.positionIdFB);
        listOfPositionIdForCount.add(instrument.positionIdSTM);
        List<String> listOfQtyForCount = new ArrayList<>();
        listOfQtyForCount.add("200");
        listOfQtyForCount.add("200");
        listOfQtyForCount.add("300");
        listOfQtyForCount.add("400");
        //Добавляем запись которую будем использовать в расчётах без NOK и AAPL positionId = null
        createMasterPortfolioWithListPosition(listOfTickersForCount, listOfTradingClearAccontsForCount, listOfPositionIdForCount, listOfQtyForCount, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
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
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку  событий
        assertThat("Нашли события в топике", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1911883")
    @Tag("qa2")
    @DisplayName("C1911883 HandleCorpActionCommand. Не нашли виртуальный портфель (master_portfolio) с самый большим version, у которого portfolio.changed_at (по МСК, округленный до даты) <= дата last_buy_date.")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1911883() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= ", strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        //Создаем запись в мастер портфеле
        List<String> listOfTickers = createTickerList(instrument.tickerAAPL, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountAAPL, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdAAPL, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.plusDays(3).toInstant()));
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
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку  событий
        assertThat("Нашли события в топике", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1913452")
    @Tag("qa2")
    @DisplayName("1913452 HandleCorpActionCommand. Не нашли виртуальный портфель (master_portfolio) с самый большим version, у которого portfolio.changed_at (по МСК, округленный до даты) <= дата last_buy_date. Найден портфель с version = 1, changed_at < last_buy_date")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1913452() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerALFAperp, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountALFAperp, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdALFAperp, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(18).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"100", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(2).toInstant()));
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
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdAAPL);
        checkdDividend(dividendList, dividendIdNOK);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем что нашли одно событие
        assertThat("Нашли события в топике", messages.size(), is(1));
        //Проверяем тело события
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(messages.get(0).getValue());
        String key = messages.get(0).getKey();
        checkPortfolioCommand(portfolioCommand, key, "35", dividendNetNOK, dividendIdNOK, Tracking.Currency.USD, "NOK", instrument.tradingClearingAccountNOK);
    }


    @SneakyThrows
    @Test
    @AllureId("1914706")
    @Tag("qa2")
    @DisplayName("1914706 HandleCorpActionCommand.Берем первый портфель после master_portfolio.changed_at (по МСК) < cut - check-portfolio-processing-days")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1914706() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDateMinus65Days);
        //Добавляем записи которые не попадают в check-portfolio-processing-days
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerALFAperp, instrument.tickerNMR, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountALFAperp, instrument.tradingClearingAccountNMR, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdALFAperp, instrument.positionIdNMR, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");
        createMasterPortfolioWithPosition(instrument.tickerNMR, instrument.tradingClearingAccountNMR, instrument.positionIdNMR, "100", positionAction, version, version,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(10).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts,  listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,
            Date.from(lastBuyDateParsed.minusDays(5).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts,  listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,
            Date.from(lastBuyDateParsed.plusDays(2).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(10).toInstant()));
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли записи в dividend, size != 1", dividendList.size(), is(1));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(5)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем тело события
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(messages.get(0).getValue());
        String key = messages.get(0).getKey();
        //нашли AAPL из-за lastBuyDay -14d, должны получить ошибку по инструменту NMR
        checkPortfolioCommand(portfolioCommand, key, "100", dividendNetAAPL, dividendIdAAPL, Tracking.Currency.USD, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("1915322")
    @Tag("qa2")
    @DisplayName("1915322 HandleCorpActionCommand. Берем первый портфель после master_portfolio.changed_at (по МСК) < cut - check-portfolio-processing-days. Нету портфелей за период (cut - check-portfolio-processing-days)")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1915322() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        //Добавляем записи которые не попадают в check-portfolio-processing-days
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerALFAperp, instrument.tickerNOK, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountALFAperp, instrument.tradingClearingAccountNOK, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdALFAperp, instrument.positionIdNOK, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");
        createMasterPortfolioWithPosition(instrument.tickerNOK, instrument.tradingClearingAccountNOK, instrument.positionIdNOK, "100", positionAction, version, version,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(65).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts,  listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(62).toInstant()));
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли записи в dividend, size != 1", dividendList.size(), is(1));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем тело события
        assertThat("Нашли события в топике", messages.size(), is(1));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(messages.get(0).getValue());
        String key = messages.get(0).getKey();
        checkPortfolioCommand(portfolioCommand, key, "35", dividendNetNOK, dividendIdNOK, Tracking.Currency.USD, "NOK", instrument.tradingClearingAccountNOK);
    }


    @SneakyThrows
    @Test
    @AllureId("1915391")
    @Tag("qa2")
    @DisplayName("1915391 HandleCorpActionCommand. Найден портфель между master_portfolio.changed_at (по МСК) < cut - check-portfolio-processing-days и last_buy_date.")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1915391() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " +  strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 1;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDateMinus65Days);
        //Добавляем записи которые не попадают в check-portfolio-processing-days
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<String> listOfTickers = createTickerList(instrument.tickerALFAperp, instrument.tickerNMR, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountALFAperp, instrument.tradingClearingAccountNMR, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdALFAperp, instrument.positionIdNMR, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("30", "35", "100", "200", "300");
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version, version,
            baseMoneyPortfolio, Date.from(cut.minusDays(67).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerNMR, instrument.tradingClearingAccountNMR, instrument.positionIdNMR, "100", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(cut.minusDays(65).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.plusDays(2).toInstant()));
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        assertThat("Нашли записи в dividend, size != 1", dividendList.size(), is(0));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем тело события
        assertThat("Нашли события в топике", messages.size(), is(0));
}


    @SneakyThrows
    @Test
    @AllureId("1866164")
    @Tag("qa2")
    @DisplayName("C1866164.HandleCorpActionCommand.  Расчет суммы начисления дивидендов RUB по одному инструменту")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1866164() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId.toString());
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //Игнорируем тикер, а получаем данные по positionId
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountSBER, instrument.positionIdSBER, "100", positionAction, version, version,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(3).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountSBER, instrument.positionIdSBER, "200", positionAction, version +1, version +1,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем запись которую будем использовать в расчётах
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountSBER, instrument.positionIdSBER, "70", positionAction, version +2, version +2,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountSBER, instrument.positionIdSBER, "100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountSBER, instrument.positionIdSBER, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountSBER, instrument.positionIdSBER, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);
        TrackingCorpAction.ActivateCorpActionCommand command = createActivateCorpActionCommand(now, cut);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        byte[] keyBytes = byteString(strategyId).toByteArray();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //Получаем позицию до обновления
        MasterPortfolio.Position positionBeforeUpdate = masterPortfolioDao.getAllMasterPortfolio(contractIdMaster, strategyId).stream()
            .filter(getVersion -> getVersion.getVersion().equals(version +2)).findFirst().get().getPositions().get(0);
        //отправляем событие в топик kafka tracking.corp-action.command
        byteToByteSenderService.send(Topics.TRACKING_CORP_ACTION_COMMAND, keyBytes, eventBytes);
        log.info("Команда в tracking.corp-action.command:  {}", command);
        await().atMost(FIVE_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdSBER);
        assertThat("Нашли записи в dividend c size != 1", dividendList.size(), is(1));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //Проверяем отправку одного событий
        assertThat("Нашли не одно событие в топике", messages.size(), is(1));
        String key = message.getKey();
        //Проверяем событие с stp-tracking-master-command (В БД не обновляем тикер, а отправляем актуальный в событии)
        checkPortfolioCommand(portfolioCommand, key, "70", dividendNetSBER, dividendIdSBER, Tracking.Currency.RUB, instrument.tickerSBER, instrument.tradingClearingAccountSBER);
    }


    @SneakyThrows
    @Test
    @AllureId("1866182")
    @Tag("qa2")
    @DisplayName("C1873536.HandleCorpActionCommand. После округления amount = 0")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C1873536() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= ", strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //После округления получим ammount = 0, а по nok qty = 0
        List<String> listOfTickers = createTickerList(instrument.tickerABBV, instrument.tickerAAPL, instrument.tickerLNT, instrument.tickerFB, instrument.tickerSTM);
        List<String> listOfTradingClearAcconts = createTradingClearAccountList(instrument.tradingClearingAccountABBV, instrument.tradingClearingAccountAAPL, instrument.tradingClearingAccountLNT, instrument.tradingClearingAccountFB, instrument.tradingClearingAccountSTM);
        List<UUID> listOfPositionIds = createPositionIdList(instrument.positionIdABBV, instrument.positionIdAAPL, instrument.positionIdLNT, instrument.positionIdFB, instrument.positionIdSTM);
        List<String> listOfQty = createQtyList("1", "0", "100", "200", "300");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(4).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        //Добавляем запись которую будем использовать в расчётах
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "100", positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);

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
        await().atMost(TEN_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdABBV);
        checkdDividend(dividendList, dividendIdAAPL);
        assertThat("Нашли записи в dividend c size != 2", dividendList.size(), is(2));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку одного событий
        assertThat("Нашли событие в топике", messages.size(), is(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1866182")
    @Tag("qa2")
    @DisplayName("C2003936.HandleCorpActionCommand. Пропускаем позицию, если не заполнен positionId")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на обработку совершенных корпоративных действий")
    void C2003936() {
        strategyId = UUID.randomUUID();
        log.info("Generated strategyId= " + strategyId);
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cut = LocalDate.now().atStartOfDay().minusHours(3).atZone(UTC).toOffsetDateTime();
        version = 2;
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего  в кассандре c позицией
        String quantityPos = "4";
        String baseMoneyPortfolio = "4990.0";
        OffsetDateTime utc = OffsetDateTime.now(UTC);
        OffsetDateTime lastBuyDateParsed = OffsetDateTime.parse(lastBuyDate);
        Date date = Date.from(utc.toInstant());
        //Создаем запись в мастер портфеле
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        //Добавляем 2 позиции
        List<String> listOfTickers = new ArrayList<>();
        listOfTickers.add(instrument.tickerAAPL);
        listOfTickers.add(instrument.tickerABBV);

        List<String> listOfTradingClearAcconts = new ArrayList<>();
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountAAPL);
        listOfTradingClearAcconts.add(instrument.tradingClearingAccountABBV);

        List<UUID> listOfPositionIds = new ArrayList<>();
        listOfPositionIds.add(null);
        listOfPositionIds.add(instrument.positionIdABBV);

        List<String> listOfQty = new ArrayList<>();
        listOfQty.add("100");
        listOfQty.add("200000");

        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version, version, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(4).toInstant()));
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +1, version +1, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(2).toInstant()));
        //Добавляем запись которую будем использовать в расчётах
        createMasterPortfolioWithListPosition(listOfTickers, listOfTradingClearAcconts, listOfPositionIds, listOfQty, version +2, version +2, baseMoneyPortfolio,  Date.from(lastBuyDateParsed.minusDays(1).toInstant()));
        //Добавляем записи которые не попадают в lastBuyDate
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, null, quantityPos, positionAction, version +3, version +3,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(1).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, null, quantityPos, positionAction, version +4, version +4,
            baseMoneyPortfolio, Date.from(lastBuyDateParsed.plusDays(8).toInstant()));
        createMasterPortfolioWithPosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, null, quantityPos, positionAction, version +5, version +5,
            baseMoneyPortfolio, date);

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
        await().atMost(TEN_SECONDS).pollDelay(FIVE_HUNDRED_MILLISECONDS).pollInterval(TWO_HUNDRED_MILLISECONDS).ignoreExceptions().until(() ->
            corpAction = corpActionService.getCorpActionByStrategyId(strategyId), notNullValue());
        //Проверяем запись в corpAction
        Optional<CorpAction> corpActionOpt = corpActionService.findCorpActionByStrategyId(strategyId);
        checkdCorpAction(corpActionOpt, cut);
        //Проверяем запись в таблице dividend
        List<Dividend> dividendList = dividendService.getDividend(strategyId);
        checkdDividend(dividendList, dividendIdABBV);
        assertThat("Нашли записи в dividend c size != 1", dividendList.size(), is(1));

        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(11)).stream()
            .filter(key -> key.getKey().equals(contractIdMaster))
            .collect(Collectors.toList());
        //Проверяем отправку одного событий
        assertThat("Нашли больще 1го события в топике", messages.size(), is(1));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(messages.get(0).getValue());
        String key = messages.get(0).getKey();
        checkPortfolioCommand(portfolioCommand, key, "200000", dividendNetABBV, dividendIdABBV, Tracking.Currency.USD, instrument.tickerABBV, instrument.tradingClearingAccountABBV);

    }

    @Step("Создаем лист с ticker инструметов:  ")
    //создаем портфель master в cassandra с позицией
    public List<String> createTickerList(String firstPosition, String secondPosition, String thirdPosition, String fourthPosition, String fifthPosition) {
        List<String> listOfTradingClearAcconts = new ArrayList<>();
        listOfTradingClearAcconts.add(firstPosition);
        listOfTradingClearAcconts.add(secondPosition);
        listOfTradingClearAcconts.add(thirdPosition);
        listOfTradingClearAcconts.add(fourthPosition);
        listOfTradingClearAcconts.add(fifthPosition);
        return listOfTradingClearAcconts;
    }

    @Step("Создаем лист tradingClearAccount инструметов:  ")
    //создаем портфель master в cassandra с позицией
    public List<String> createTradingClearAccountList(String firstPosition, String secondPosition, String thirdPosition, String fourthPosition, String fifthPosition) {
        List<String> listOfQty = new ArrayList<>();
        listOfQty.add(firstPosition);
        listOfQty.add(secondPosition);
        listOfQty.add(thirdPosition);
        listOfQty.add(fourthPosition);
        listOfQty.add(fifthPosition);
        return listOfQty;
    }

    @Step("Создаем лист positionId инструметов:  ")
    //создаем портфель master в cassandra с позицией
    public List<UUID> createPositionIdList (UUID firstPositionId, UUID secondPositionId, UUID thirdPositionId, UUID fourthPositionId, UUID fifthPositionId) {
        List<UUID> listOfQty = new ArrayList<>();
        listOfQty.add(firstPositionId);
        listOfQty.add(secondPositionId);
        listOfQty.add(thirdPositionId);
        listOfQty.add(fourthPositionId);
        listOfQty.add(fifthPositionId);
        return listOfQty;
    }

    @Step("Создаем лист с qty инструметов:  ")
        //создаем портфель master в cassandra с позицией
    public List<String> createQtyList(String firstPosition, String secondPosition, String thirdPosition, String fourthPosition, String fifthPosition) {
        List<String> listOfQty = new ArrayList<>();
        listOfQty.add(firstPosition);
        listOfQty.add(secondPosition);
        listOfQty.add(thirdPosition);
        listOfQty.add(fourthPosition);
        listOfQty.add(fifthPosition);
       return listOfQty;
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



    public ByteString byteString(UUID uuid) {
            return ByteString.copyFrom(bytes(uuid));
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

    //создаем портфель master в cassandra с позицией
    void createMasterPortfolioWithListPosition(List<String> listOfTickers, List<String> listOfTradingClearAcconts, List<UUID> positionIds, List<String> listOfQty, int versionPos, int version, String money, Date date) {

        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();

        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        for (int i = 0; i < listOfTickers.size(); i++) {
            positionList.add(MasterPortfolio.Position.builder()
                .ticker(listOfTickers.get(i))
                .tradingClearingAccount(listOfTradingClearAcconts.get(i))
                .positionId(positionIds.get(i))
                .lastChangeAction((byte) positionAction.getAction().getActionValue())
                .lastChangeDetectedVersion(versionPos)
                .changedAt(date)
                .quantity(new BigDecimal(listOfQty.get(i)))
                .build());
        }
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }

    void checkdDividend(List<Dividend> dividendList, String dividendId){
        Dividend getdividend = dividendList.stream()
            .filter(id -> id.getId().equals(Long.valueOf(dividendId)))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Не нашли дивиденд"));
        assertThat("dividendId != " + dividendId, getdividend.getId(), is(Long.valueOf(dividendId)));
        assertThat("strategyId != " + strategyId, getdividend.getStrategyId(), is(strategyId));
        //assertThat("created_at != now()", getDateFromDividend.toString().substring(0, 15), is(now.toString().substring(0, 15)));
    }

    void checkdCorpAction (Optional<CorpAction> corpActionOpt,  OffsetDateTime cut){
        assertThat("strategyId != " + strategyId, corpActionOpt.get().getStrategyId(), is(strategyId));
        assertThat("type != " + typeDVCA, corpActionOpt.get().getType(), is(typeDVCA));
        assertThat("cut != now() + 00:00:00", corpActionOpt.get().getCut(), is(cut.toLocalDateTime().plusHours(3)));
    }

    void checkPortfolioCommand (Tracking.PortfolioCommand portfolioCommand, String key, String qtyFromPosition, String dividendNet, String dividendId, Tracking.Currency currency, String ticker, String tradingClearingAcoount){
        BigDecimal amountFromMessage = BigDecimal.valueOf(portfolioCommand.getDividend().getAmount().getUnscaled(), portfolioCommand.getDividend().getAmount().getScale());
        BigDecimal amountAfterCalculated = calculateAmount(qtyFromPosition, dividendNet);
        LocalDateTime getDateFromMessage = LocalDateTime.ofEpochSecond(portfolioCommand.getCreatedAt().getSeconds(), portfolioCommand.getCreatedAt().getNanos(), ZoneOffset.of("+03:00"));
        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC+03:00"));
        assertThat("Key  != contract_id", key, is(contractIdMaster));
        assertThat("contract_id != strategy.contract_id", portfolioCommand.getContractId(), is(contractIdMaster));
        assertThat("operation != 'ACTUALIZE'", portfolioCommand.getOperation(), is(Tracking.PortfolioCommand.Operation.ACTUALIZE));
        assertThat("created_at != now()", ZonedDateTime.of(getDateFromMessage, ZoneId.of("UTC+03:00")).toString().substring(0, 15), is(now.toString().substring(0, 15)));
        assertThat("dividend.id != dividend.id", portfolioCommand.getDividend().getId(), is(Long.valueOf(dividendId)));
        assertThat("dividend.exchange_position_id.ticker != exchangePosition.ticker", portfolioCommand.getDividend().getExchangePositionId().getTicker(), is(ticker));
        assertThat("dividend.exchange_position_id.trading_clearing_account != exchangePosition.trading_clearing_account", portfolioCommand.getDividend().getExchangePositionId().getTradingClearingAccount(), is(tradingClearingAcoount));
        assertThat("dividend.amount != amount", amountFromMessage, is(amountAfterCalculated));
        assertThat("dividend.currency != dividend.dividend_currency", portfolioCommand.getDividend().getCurrency(), is(currency));
    }

    public BigDecimal calculateAmount (String qty, String dividendNet){
        //amount = master_portfolio.positions.quantity * dividend.dividend_net * (1 - значение настройки dividend-tax-rate (0.13)).
        BigDecimal amount = new BigDecimal(qty)
            .multiply(new BigDecimal(dividendNet))
            .multiply(new BigDecimal("1").subtract(new BigDecimal(dividendTaxRate)))
            .divide(new BigDecimal("1"), 2, RoundingMode.HALF_UP);
        return amount;
    }

    @Step("Проверяем запись в таблице devidend: \n {dividend}")
    void checkDividend(Tracking.PortfolioCommand dividendCommand, ru.qa.tinkoff.investTracking.entities.Dividend dividend, BigDecimal amount){
        assertThat("contract_id != " + contractIdMaster, dividend.getContractId(), is(contractIdMaster));
        assertThat("strategy_id != " + strategyId, dividend.getStrategyId(), is(strategyId));
        assertThat("id != dividend.id из входных параметров" + dividendCommand.getDividend().getId(), dividend.getId(), is(dividendCommand.getDividend().getId()));
        assertThat("context.amount  != dividend.amount из входных параметров", dividend.getContext().getAmount(), is(amount));
    }

    @Step("Проверяем запись в таблице masterPortfolio: \n {masterPortfolio}")
    void checkMasterPortfolio(MasterPortfolio masterPortfolio,  BigDecimal baseMoneyPosition){
        assertThat("Не увеличили базовую валюту на значение дивидента", masterPortfolio.getBaseMoneyPosition().getQuantity(), is(baseMoneyPosition));
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

    @Step("Создаем мок, для ticker = {ticker} и classCode = {classCode}")
    void createMockForGetDividendsWithOneItems (String ticker, String classCode, String dividendId, String instrumentId, String dividendNet, String dividendCurrency, String paymentDate, String lastBuyDate, String status){
                getDividendsSteps.createGetDividends(getDividendsSteps.createBodyForGetDividendWithOneElement(ticker, classCode, dividendId, instrumentId,
                    dividendNet, dividendCurrency, paymentDate, lastBuyDate, status));
    }

    @Step("Создаем мок, для ответа метода account/public/v1/invest/siebel")
    void createDataForMockRestAccount (String investIdMaster, String siebelIdMaster, String contractIdMaster){
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster, siebelIdMaster, contractIdMaster));
    }
}
