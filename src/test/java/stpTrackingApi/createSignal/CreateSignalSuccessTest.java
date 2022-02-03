package stpTrackingApi.createSignal;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateSignalRequest;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;

@Slf4j
@Epic("createSignal - Создание торгового сигнала")
@Feature("TAP-8619")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("createSignal")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {

    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingAdminStepsConfiguration.class
})
public class CreateSignalSuccessTest {

    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StringSenderService kafkaSender;
    @Autowired
    TrackingService trackingService;
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
    MasterSignalDao masterSignalDao;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StpTrackingAdminSteps adminSteps;

    ExchangePositionApi exchangePositionApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.
        api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();

    SignalApi signalApi = ApiClient.api(ApiClient.Config.apiConfig()).signal();
    String contractId;
    UUID strategyId;
    String contractIdMaster;
    int versionNew;
    MasterSignal masterSignal;

    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String tickerBond = "ALFAperp";
    String tradingClearingAccountBond = "TKCBM_TCAB";
    String instrumentBond = "ALFAperp_SPBBND";
    String ticker1 = "YNDX";
    String tradingClearingAccount1 = "Y02+00001F00";
    String SIEBEL_ID = "1-1P424JS";

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
                masterPortfolioDao.deleteMasterPortfolio(contractId, strategyId);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignal(strategyId, versionNew);
            } catch (Exception e) {
            }
        });
    }

    String description = "new test стратегия autotest";

    @SneakyThrows
    @Test
    @AllureId("653779")
    @DisplayName("C653779.CreateSignal.Создания торгового сигнала ведущим, action = buy, позиция не найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C653779() {
        double money = 1500.0;
        BigDecimal price = new BigDecimal("107.0");
        int quantityRequest = 3;
        int version = 1;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.ACTUALIZE));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdMaster));
//        assertThat("createAt не равен", time.toInstant().truncatedTo(ChronoUnit.SECONDS),
//            is(createAt.truncatedTo(ChronoUnit.SECONDS)));
        log.info("Команда в tracking.master.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money - (price.multiply(new BigDecimal(quantityRequest))).floatValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest;
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, ticker, tradingClearingAccount);
    }


    @SneakyThrows
    @Test
    @AllureId("659115")
    @DisplayName("C659115.CreateSignal.Создания торгового сигнала ведущим, action = buy, позиция найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C659115() {
        double money = 3500.0;
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 2;
        double quantityPosMasterPortfolio = 12.0;
        //находим данные ведущего в БД сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.slave.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money - (price.multiply(new BigDecimal(quantityRequest))).floatValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = quantityPosMasterPortfolio + quantityRequest;
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        versionNew = version + 1;
        // проверяем значения в полученной команде
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, ticker, tradingClearingAccount);
    }


    @SneakyThrows
    @Test
    @AllureId("659236")
    @DisplayName("C659236.CreateSignal.Создания торгового сигнала ведущим, action =sell, позиция найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C659236() {
        double money = 3500.0;
        BigDecimal price = new BigDecimal("10.0");
        int quantityRequest = 4;
        int version = 3;
        double quantityPosMasterPortfolio = 12.0;
        //находим данные ведущего в БД сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking статегию на ведущего
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL, price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        log.info("Команда в tracking.slave.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money + (price.multiply(new BigDecimal(quantityRequest))).floatValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = quantityPosMasterPortfolio - quantityRequest;
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем параметры полученной команды
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 11, "SECURITY_SELL_TRADE", quantityReqBaseMoney, price,
            quantityRequest, ticker, tradingClearingAccount);
    }


    @SneakyThrows
    @Test
    @AllureId("1312630")
    @DisplayName("C1312630.CreateSignal.Создание торгового сигнала для bond")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1312630() {
        double money = 1500.0;
        int quantityRequest = 3;
        int version = 1;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(tickerBond, tradingClearingAccountBond, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        BigDecimal price = new BigDecimal(steps.getPriceFromMarketData(instrumentBond, "last"));
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            tickerBond, tradingClearingAccountBond, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        List<String> dateBond = steps.getPriceFromExchangePositionCache(tickerBond, tradingClearingAccountBond, SIEBEL_ID);
        String aciValue = dateBond.get(0);
        BigDecimal priceBond = price.add(new BigDecimal(aciValue));
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.ACTUALIZE));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdMaster));
        log.info("Команда в tracking.master.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMon = money - (priceBond.multiply(new BigDecimal(quantityRequest))).floatValue();
        BigDecimal quantityReqBaseMoney = new BigDecimal(Double.toString(quantityReqBaseMon));
        quantityReqBaseMoney = quantityReqBaseMoney.setScale(2, RoundingMode.HALF_UP);
        double quantityCommandBaseMon = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        BigDecimal quantityCommandBaseMoney = new BigDecimal(Double.toString(quantityCommandBaseMon));
        quantityCommandBaseMoney = quantityCommandBaseMoney.setScale(2, RoundingMode.HALF_UP);
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest;
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommandBond(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, priceBond,
            quantityRequest, tickerBond, tradingClearingAccountBond);
    }


    @SneakyThrows
    @Test
    @AllureId("1434620")
    @DisplayName("C1434620.CreateSignal.SignalPreChecks.Расчет количества единиц актива в заявках ведомых.Action=Buy.Type=Share")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1434620() {
        double money = 1500.0;
        BigDecimal price = new BigDecimal("107.0");
        int quantityRequest = 6;
        int version = 1;
        String tailValue = "6259.17";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем запись в strategy_tail_value
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_MASTER_COMMAND, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(message.getValue());
        //рассчитываем текущую общую стоимость портфеля portfolioValue без учета выставляемого сигнала:
        // сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(money));
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = new BigDecimal(Integer.toString(quantityRequest)).multiply(price);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue, 4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue = new BigDecimal(tailValue).multiply(signalRate);
        //получаем стоимость позиции, из кэша exchangePositionPriceCache если action обрабатываемого сигнала = 'buy', то price_type = 'ask'
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(ticker, tradingClearingAccount, "ask", SIEBEL_ID);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceAsk), 0, RoundingMode.HALF_UP);
        //ждем появляения записи в табл. masterSignal запись о выставленном сигнале и проверяем значение TailOrderQuantity
        checkMasterSignal(strategyId, version + 1);
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        assertThat("количества единиц актива в заявках ведомых в БД не равен", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
        assertThat("количество единиц актива в событии не равен", commandKafka.getSignal().getTailOrderQuantity().getUnscaled(), is(tailOrderQuantity.longValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("1434604")
    @DisplayName("C1434604.CreateSignal.SignalPreChecks.Расчет количества единиц актива в заявках ведомых.Action=Sell.Type=Share")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1434604() {
        double money = 1500.0;
        BigDecimal price = new BigDecimal("108.0");
        int quantityRequest = 3;
        int version = 2;
        String tailValue = "6259.17";
        double quantityPosMasterPortfolio = 6.0;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем запись в strategy_tail_value
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //создаем запись о прошлом сигнале в master_signal
        steps.createMasterSignal(0, 0, version, strategyId, ticker, tradingClearingAccount,
            "107", "6", "25", 12);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL, price, quantityRequest, strategyId,
            ticker, tradingClearingAccount, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //получаем стоимость позиции, которая есть у мастера из  кэша exchangePositionPriceCache
        String priceLast = steps.getPriceFromExchangePositionPriceCache(ticker, tradingClearingAccount, "last", SIEBEL_ID);
        //рассчитываем текущую общую стоимость портфеля portfolioValue без учета выставляемого сигнала:
        //сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(new BigDecimal(priceLast))
            .add(new BigDecimal(Double.toString(money)));
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = new BigDecimal(Integer.toString(quantityRequest)).multiply(price);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue, 4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue = new BigDecimal(tailValue).multiply(signalRate);
        //получаем стоимость позиции,  из  кэша exchangePositionPriceCache если action обрабатываемого сигнала = 'sell' price_type = 'bid';
        String priceBid = steps.getPriceFromExchangePositionPriceCache(ticker, tradingClearingAccount, "bid", SIEBEL_ID);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceBid), 0, RoundingMode.HALF_UP);
        //ждем появляения записи в табл. masterSignal запись о выставленном сигнале и проверяем значение TailOrderQuantity
        checkMasterSignal(strategyId, version + 1);
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        assertThat("количества единиц актива в заявках ведомых не равен", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }


    @SneakyThrows
    @Test
    @AllureId("1434098")
    @DisplayName("C1434098.CreateSignal.SignalPreChecks.Расчет количества единиц актива в заявках ведомых.Action=Sell.Type=Bond")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1434098() {
        double money = 1500.0;
        int quantityRequest = 1;
        int version = 2;
        String tailValue = "6259.17";
        double quantityPosMasterPortfolio = 3.0;
        BigDecimal minPriceIncrement = new BigDecimal("0.01");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, tickerBond, tradingClearingAccountBond, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        steps.createMasterSignal(0, 0, version, strategyId, ticker, tradingClearingAccount,
            "103.05", "3", "1", 12);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(tickerBond, tradingClearingAccountBond, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        BigDecimal price = new BigDecimal(steps.getPriceFromMarketData(instrumentBond, "last"));
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            tickerBond, tradingClearingAccountBond, version);
        // вызываем метод CreateSignal
        signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //рассчитываем текущую общую стоимость портфеля portfolioValue без учета выставляемого сигнала:
        String priceLast = steps.getPriceFromExchangePositionPriceCache(ticker, tradingClearingAccount, "last", SIEBEL_ID);
        List<String> dateBond = steps.getPriceFromExchangePositionCache(tickerBond, tradingClearingAccountBond, SIEBEL_ID);
        String aciValue = dateBond.get(0);
        String nominal = dateBond.get(1);
        BigDecimal priceLastBond = steps.valuePosBonds(priceLast, nominal, minPriceIncrement, aciValue);
        // сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(priceLastBond)
            .add(new BigDecimal(Double.toString(money)));
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = new BigDecimal(Integer.toString(quantityRequest)).multiply(price);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue, 4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue = new BigDecimal(tailValue).multiply(signalRate);
        //получаем стоимость позиции, из кэша exchangePositionPriceCache если action обрабатываемого сигнала = 'buy', то price_type = 'ask'
        String priceBid = steps.getPriceFromExchangePositionPriceCache(ticker, tradingClearingAccount, "bid", SIEBEL_ID);
        //рассчитываем цену по bond в абсолютном значении
        BigDecimal priceBidBond = steps.valuePosBonds(priceBid, nominal, minPriceIncrement, aciValue);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(priceBidBond, 0, RoundingMode.HALF_UP);
        //ждем появляения записи в табл. masterSignal запись о выставленном сигнале и проверяем значение TailOrderQuantity
        checkMasterSignal(strategyId, version + 1);
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        assertThat("количества единиц актива в заявках ведомых не равен", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }


    @SneakyThrows
    @Test
    @AllureId("1439791")
    @DisplayName("1439791 Объем заявки. Покупка. period = default. tailOrderQuantity < limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1439791() {
        BigDecimal price = new BigDecimal("5341.8");
        int quantityRequest = 3;
        int version = 4;
        String tailValue = "150000";
        double money = 100000.0;
        double quantityPosMasterPortfolio = 0.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWintContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
/*        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, ticker, tradingClearingAccount,
            "0");*/
        steps.createMasterPortfolio(contractIdMaster, strategyId, null, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(ticker1, tradingClearingAccount1, ExchangePosition.ExchangeEnum.MOEX,
            true, 22845, orderQuantityList(100, "default"));
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, ticker1, tradingClearingAccount1, version);
        // вызываем метод CreateSignal
        Response createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
        //рассчёты
        // сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(price)
            .add(new BigDecimal(Double.toString(money)));
        //Получаем цену покупки
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(ticker1, tradingClearingAccount1, "ask", SIEBEL_ID);
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = price.multiply(BigDecimal.valueOf(quantityRequest));
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(signalRate);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceAsk), 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("price", masterSignal.getPrice(), is(price));
        assertThat("quantity", masterSignal.getQuantity().intValue(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(ticker1));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(tradingClearingAccount1));
        assertThat("tailOrderQuantity", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }



    //*** Методы для работы тестов ***
    //Метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
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
                .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
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

    List<OrderQuantityLimit> orderQuantityList(int limit, String period) {
        List<OrderQuantityLimit> orderQuantityLimitList
            = new ArrayList<>();
        OrderQuantityLimit orderQuantityLimit = new OrderQuantityLimit();
        orderQuantityLimit.setLimit(limit);
        orderQuantityLimit.setPeriodId(period);
        orderQuantityLimitList.add(orderQuantityLimit);
        return orderQuantityLimitList;
    }


    void assertCommand(Tracking.PortfolioCommand portfolioCommand, String key, int version, double quantityPositionCommand,
                       double quantityCommandBaseMoney, double quantityPosition, int actionValue, String action, double quantityReqBaseMoney, BigDecimal price,
                       int quantityRequest, String ticker, String tradingClearingAccount) {
        assertThat("ID договора мастера не равен", portfolioCommand.getContractId(), is(contractIdMaster));
        assertThat("operation команды по актуализации мастера не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("дата команды по инициализации мастера не равен", dateFromCommandWithMinut, is(dateNow));
        assertThat("ключ команды по актуализации мастера  не равен", key, is(contractIdMaster));
        assertThat("номер версии  мастера  не равен", portfolioCommand.getPortfolio().getVersion(), is(version + 1));
        assertThat("ticker бумаги  не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount бумаги  не равен", portfolioCommand.getPortfolio().getPosition(0).
            getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", quantityPositionCommand, is(quantityPosition));
        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).
            getAction().getActionValue(), is(actionValue));
        assertThat("значение action  не равен", portfolioCommand.getPortfolio().getPosition(0).
            getAction().getAction().toString(), is(action));
        assertThat("quantity базовой валюты  не равен", quantityCommandBaseMoney, is(quantityReqBaseMoney));
        double pricePositionCommand = portfolioCommand.getSignal().getPrice().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getSignal().getPrice().getScale());
        assertThat("значение price  не равен", BigDecimal.valueOf(pricePositionCommand), is(price));
        double quaSignalPositionCommand = portfolioCommand.getSignal().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getSignal().getQuantity().getScale());
        assertThat("значение quantity  не равен", quaSignalPositionCommand, is(Double.valueOf(quantityRequest)));
    }


    void assertCommandBond(Tracking.PortfolioCommand portfolioCommand, String key, int version, double quantityPositionCommand,
                           BigDecimal quantityCommandBaseMoney, double quantityPosition, int actionValue, String action, BigDecimal quantityReqBaseMoney, BigDecimal price,
                           int quantityRequest, String ticker, String tradingClearingAccount) {
        assertThat("ID договора мастера не равен", portfolioCommand.getContractId(), is(contractIdMaster));
        assertThat("operation команды по актуализации мастера не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("дата команды по инициализации мастера не равен", dateFromCommandWithMinut, is(dateNow));
        assertThat("ключ команды по актуализации мастера  не равен", key, is(contractIdMaster));
        assertThat("номер версии  мастера  не равен", portfolioCommand.getPortfolio().getVersion(), is(version + 1));
        assertThat("ticker бумаги  не равен", portfolioCommand.getPortfolio().getPosition(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount бумаги  не равен", portfolioCommand.getPortfolio().getPosition(0).
            getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("quantity позиции не равен", quantityPositionCommand, is(quantityPosition));
        assertThat("action  не равен", portfolioCommand.getPortfolio().getPosition(0).
            getAction().getActionValue(), is(actionValue));
        assertThat("значение action  не равен", portfolioCommand.getPortfolio().getPosition(0).
            getAction().getAction().toString(), is(action));
        assertThat("quantity базовой валюты  не равен", quantityCommandBaseMoney, is(quantityReqBaseMoney));
        double pricePositionCom = portfolioCommand.getSignal().getPrice().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getSignal().getPrice().getScale());
        BigDecimal pricePositionCommand = new BigDecimal(Double.toString(pricePositionCom));
        pricePositionCommand = pricePositionCommand.setScale(2, RoundingMode.HALF_UP);
        price = price.setScale(2, RoundingMode.HALF_UP);
        assertThat("значение price  не равен", pricePositionCommand, is(price));
        double quaSignalPositionCommand = portfolioCommand.getSignal().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getSignal().getQuantity().getScale());
        assertThat("значение quantity  не равен", quaSignalPositionCommand, is(Double.valueOf(quantityRequest)));
    }


    public CreateSignalRequest createSignalRequest(CreateSignalRequest.ActionEnum actionEnum, BigDecimal price,
                                                   int quantityRequest, UUID strategyId, String ticker,
                                                   String tradingClearingAccount, int version) {
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(actionEnum);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
        return request;
    }

    // ожидаем версию портфеля slave
    void checkMasterSignal(UUID strategyId, int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(5000);
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version);
            if (masterSignal.getVersion() != version) {
                Thread.sleep(3000);
            }
        }
    }

}
