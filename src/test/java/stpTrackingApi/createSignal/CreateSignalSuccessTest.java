package stpTrackingApi.createSignal;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaMessageConsumer;
import ru.qa.tinkoff.kafka.model.KafkaModelFiregInstrumentWayfairWithRiskEvent;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateSignalRequest;
import ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.CreateExchangePositionRequest;
import ru.qa.tinkoff.swagger.tracking_admin.model.ExchangePosition;
import ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("createSignal - Создание торгового сигнала")
@Feature("TAP-8619")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class})
public class CreateSignalSuccessTest {
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
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    MasterSignalDao masterSignalDao;

    ExchangePositionApi exchangePositionApi= ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).exchangePosition();
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    SignalApi signalApi = ApiClient.api(ApiClient.Config.apiConfig()).signal();
    Client client;
    Contract contract;
    Strategy strategy;
    String contractId;
    UUID strategyId;
    int versionNew;
    String ticker = "XS0587031096";
    String tradingClearingAccount = "L01+00000SPB";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
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

    @SneakyThrows
    @Test
    @AllureId("653779")
    @DisplayName("C653779.CreateSignal.Создания торгового сигнала ведущим, action = buy, позиция не найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C653779() {
        String SIEBEL_ID = "1-1P424JS";
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        double money = 1500.0;
        double price = 10.0;
        int quantityRequest = 3;
        int version = 1;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем стратегию на ведущего
        strategyId = createClientWithStrategy(SIEBEL_ID, investId, ClientStatusType.registered, Double.toString(money),
            StrategyBaseCurrency.USD, StrategyRiskProfile.AGGRESSIVE);
        contract = contractService.getContract(contractId);
        strategy = strategyService.getStrategy(strategyId);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //включаем kafkaConsumer и слушаем топик tracking.master.command
        List<KafkaMessageConsumer.Record<String, byte[]>> records = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);
            messageConsumer.startUp();
            //формируем тело запроса метода CreateSignal
            CreateSignalRequest request = new CreateSignalRequest();
            request.setAction(CreateSignalRequest.ActionEnum.BUY);
            request.setContractId(contractId);
            request.setPrice(price);
            request.setQuantity(quantityRequest);
            request.setStrategyId(strategyId);
            request.setTicker(ticker);
            request.setTradingClearingAccount(tradingClearingAccount);
            request.setVersion(version);
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
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractId.equals(portfolioCommandBefore.getContractId())
                & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
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
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money - (quantityRequest * price);
        double quantityCommandBaseMoney = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest;
        double quantityPositionCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
       versionNew = version + 1;
        assertCommand(portfolioCommand, key, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price, quantityRequest);
    }


    @SneakyThrows
    @Test
    @AllureId("659115")
    @DisplayName("C659115.CreateSignal.Создания торгового сигнала ведущим, action = buy, позиция найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C659115() {
        String SIEBEL_ID = "1-2X5IYXJ";
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        double money = 3500.0;
        double price = 10.0;
        int quantityRequest = 4;
        int version = 1;
        double quantityPosMasterPortfolio = 12.0;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, Double.toString(quantityPosMasterPortfolio), Double.toString(money));
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //включаем kafkaConsumer и слушаем топик tracking.master.command
        List<KafkaMessageConsumer.Record<String, byte[]>> records = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);

            messageConsumer.startUp();
            //формируем тело запроса метода CreateSignal
            CreateSignalRequest request = new CreateSignalRequest();
            request.setAction(CreateSignalRequest.ActionEnum.BUY);
            request.setContractId(contractId);
            request.setPrice(price);
            request.setQuantity(quantityRequest);
            request.setStrategyId(strategyId);
            request.setTicker(ticker);
            request.setTradingClearingAccount(tradingClearingAccount);
            request.setVersion(version);
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
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractId.equals(portfolioCommandBefore.getContractId())
                & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
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
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money - (quantityRequest * price);
        double quantityCommandBaseMoney = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = quantityPosMasterPortfolio + quantityRequest;
        double quantityPositionCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());

        versionNew = version + 1;
        // проверяем значения в полученной команде
        assertCommand(portfolioCommand, key, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price, quantityRequest);
    }


    @SneakyThrows
    @Test
    @AllureId("659236")
    @DisplayName("C659236.CreateSignal.Создания торгового сигнала ведущим, action =sell, позиция найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C659236() {
        String SIEBEL_ID = "1-2OXURAW";
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        LocalDateTime dateCreateTr = null;
        Tracking.PortfolioCommand portfolioCommand = null;
        String key = null;
        double money = 3500.0;
        double price = 10.0;
        int quantityRequest = 4;
        int version = 3;
        double quantityPosMasterPortfolio = 12.0;
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем в БД tracking статегию на ведущего
        strategyId = UUID.randomUUID();
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, Double.toString(quantityPosMasterPortfolio), Double.toString(money));
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //включаем kafkaConsumer и слушаем топик tracking.master.command
        List<KafkaMessageConsumer.Record<String, byte[]>> records = null;
        try (KafkaMessageConsumer<String, byte[]> messageConsumer =
                 new KafkaMessageConsumer<>(kafkaHelper, "tracking.master.command",
                     StringDeserializer.class, ByteArrayDeserializer.class)) {
            messageConsumer.setTimeout(5000);

            messageConsumer.startUp();
            //формируем тело запроса метода CreateSignal
            CreateSignalRequest request = new CreateSignalRequest();
            request.setAction(CreateSignalRequest.ActionEnum.SELL);
            request.setContractId(contractId);
            request.setPrice(price);
            request.setQuantity(quantityRequest);
            request.setStrategyId(strategyId);
            request.setTicker(ticker);
            request.setTradingClearingAccount(tradingClearingAccount);
            request.setVersion(version);
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
            Thread.sleep(5000);
            messageConsumer.await()
                .orElseThrow(() -> new RuntimeException("Команда не получена"));
            records = messageConsumer.listRecords();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        for (int i = 0; i < records.size(); i++) {
            Tracking.PortfolioCommand portfolioCommandBefore = Tracking.PortfolioCommand.parseFrom(records.get(i).value);
            if (contractId.equals(portfolioCommandBefore.getContractId())
                & ("ACTUALIZE".equals(portfolioCommandBefore.getOperation().toString()))) {
                portfolioCommand = portfolioCommandBefore;
                key = records.get(i).key;
                break;
            }
        }
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money + (quantityRequest * price);
        double quantityCommandBaseMoney = portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = quantityPosMasterPortfolio - quantityRequest;
        double quantityPositionCommand = portfolioCommand.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем параметры полученной команды
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(portfolioCommand, key, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 11, "SECURITY_SELL_TRADE", quantityReqBaseMoney, price, quantityRequest);
    }


    private static Stream<Arguments> provideRiskLevelOk() {
        return Stream.of(
            Arguments.of("0", ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive),
            Arguments.of("1", ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive),
            Arguments.of("1", ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.moderate),
            Arguments.of("2", ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive),
            Arguments.of("2", ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.moderate),
            Arguments.of("2", ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRiskLevelOk")
    @AllureId("660682")
    @DisplayName("C660682.CreateSignal.Риск-профиль позиции не превышает риск-профиль стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C660682(String riskInst, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile) {
        String SIEBEL_ID = "5-DMYMSG6P";
        double price = 10.0;
        int quantityRequest = 3;
        int version = 4;
        versionNew = version + 1;
        String ticker = "W";
        String tradingClearingAccount = "TKCBM_TCAB";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        //отправляем событие в fireg.instrument
        String event = KafkaModelFiregInstrumentWayfairWithRiskEvent.getKafkaTemplate(LocalDateTime.now(), riskInst);
        String key = "BBG001B17MV2";
        //отправляем событие в топик kafka social.event
        KafkaTemplate<String, String> template = kafkaHelper.createStringToStringTemplate();
        template.setDefaultTopic("fireg.instrument");
        template.sendDefault(key, event);
        template.flush();
        //находим данные ведущего в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        createClientWintContractAndStrategy(investId, contractId, null, ContractState.untracked,
            strategyId, StrategyCurrency.usd, strategyRiskProfile, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        createMasterPortfolio(ticker, tradingClearingAccount, version, "12.0", "3556.78");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        getExchangePosition(ticker, tradingClearingAccount, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = new CreateSignalRequest();
        request.setAction(CreateSignalRequest.ActionEnum.BUY);
        request.setContractId(contractId);
        request.setPrice(price);
        request.setQuantity(quantityRequest);
        request.setStrategyId(strategyId);
        request.setTicker(ticker);
        request.setTradingClearingAccount(tradingClearingAccount);
        request.setVersion(version);
        // вызываем метод CreateSignal
        SignalApi.CreateSignalOper createSignal = signalApi.createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202));
        createSignal.execute(ResponseBodyData::asString);

    }


    /////////***методы для работы тестов**************************************************************************

    //метод находит подходящий siebеlId в сервисе счетов и создаем запись по нему в табл. tracking.client
    UUID createClientWithStrategy(String SIEBEL_ID, UUID investId, ClientStatusType сlientStatusType, String money,
                                  ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency baseCurrency,
                                  ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile riskProfile) {
        client = clientService.createClient(investId, сlientStatusType, null);
        //формируем тело запроса метода createStrategy
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(baseCurrency);
        request.setDescription("test strategy by autotest");
        request.setRiskProfile(riskProfile);
        request.setTitle("test strategy createSignal");
        request.setBaseMoneyPositionQuantity(new BigDecimal(money));
        // вызываем метод CreateStrategy
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(ru.qa.tinkoff.swagger.tracking.model.CreateStrategyResponse.class));
        //достаем из response идентификатор стратегии
        UUID strategyId = UUID.fromString(expectedResponse.getStrategy().getId().toString());
        return strategyId;
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

    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(UUID investId, String contractId, ContractRole contractRole,
                                             ContractState contractState, UUID strategyId, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) throws JsonProcessingException {

        //создаем запись о клиенте в tracking.client
        client = clientService.createClient(investId, ClientStatusType.registered, null);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
        //создаем запись о стратегии клиента
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle("test strategy by autotest")
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription("test strategy createSignal")
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategy = strategyService.saveStrategy(strategy);
    }

    void createMasterPortfolio(String ticker, String tradingClearingAccount, int version, String quantityPos, String money) {
        //создаем портфель master в cassandra
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantityPos))
            .build());
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolio(contractId, strategyId, version, baseMoneyPosition, positionList);
    }


    void assertCommand(Tracking.PortfolioCommand portfolioCommand, String key, int version, double quantityPositionCommand,
                       double quantityCommandBaseMoney, double quantityPosition, int actionValue, String action, double quantityReqBaseMoney, double price,
                       int quantityRequest) {
        assertThat("ID договора мастера не равен", portfolioCommand.getContractId(), is(contractId));
        assertThat("operation команды по актуализации мастера не равен", portfolioCommand.getOperation().toString(), is("ACTUALIZE"));
//        assertThat("дата команды по инициализации мастера не равен", dateFromCommandWithMinut, is(dateNow));
        assertThat("ключ команды по актуализации мастера  не равен", key, is(contractId));
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
        assertThat("значение price  не равен", pricePositionCommand, is(price));
        double quaSignalPositionCommand = portfolioCommand.getSignal().getQuantity().getUnscaled()
            * Math.pow(10, -1 * portfolioCommand.getSignal().getQuantity().getScale());
        assertThat("значение quantity  не равен", quaSignalPositionCommand, is(Double.valueOf(quantityRequest)));
    }
}
