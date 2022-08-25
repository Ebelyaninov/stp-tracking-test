package stpTrackingApi.createSignal;

import com.google.protobuf.ByteString;
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
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.investTracking.services.StrategyTailValueDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.mocks.steps.MocksBasicStepsConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.*;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SignalApi;
import ru.qa.tinkoff.swagger.tracking.model.CreateSignalRequest;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.*;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
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
    KafkaOldConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    AdminApiCreatorConfiguration.class,
    MocksBasicStepsConfiguration.class,
    StpTrackingMockSlaveDateConfiguration.class

})
public class CreateSignalSuccessTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    TrackingService trackingService;
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
    StrategyTailValueDao strategyTailValueDao;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StpTrackingAdminSteps adminSteps;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<SignalApi> signalApiCreator;
    @Autowired
    ApiAdminCreator<ExchangePositionApi> exchangePositionApiAdminCreator;
    @Autowired
    StringToByteSenderService stringToByteSenderService;

    UUID strategyId;
    String contractIdMaster;
    int versionNew;
    MasterSignal masterSignal;
    String SIEBEL_ID;
    UUID strategyIdMaxCount = UUID.fromString("982ec1ce-787e-43e2-89b9-5a7466cd581d");
    UUID investIdMaster;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID = stpSiebel.siebelIdApiMaster;
        //mocksBasicSteps.createDataForMasterMockApi(SIEBEL_ID);
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createCommandForStatusCache(instrument.tickerSBER, instrument.classCodeSBER, "normal_trading",  LocalDateTime.now().minusHours(4));
        steps.createCommandForStatusCache(instrument.tickerAAPL, instrument.classCodeAAPL, "normal_trading",  LocalDateTime.now().minusHours(4));
        //Добавил, ради обновление лимитов
        updateExchangePosition();
        //steps.deleteDataFromDb(SIEBEL_ID);
        try {
            strategyService.deleteStrategy(strategyService.findStrategyByContractId(contractIdMaster).get());
        } catch (Exception e) {
        }
        try {
            contractService.deleteContractById(contractIdMaster);
        } catch (Exception e) {
        }
        try {
            clientService.deleteClient(steps.clientMaster);
        } catch (Exception e) {
        }
    }

    @AfterAll
    void changePositionLimit (){
        updateExchangePositionCacheLimit(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 1000, 10,10,10,10);
    }


    @BeforeEach
    void changePositionLimitBeforeEach (){
        //Обновляем данные по лимитам
        updateExchangePositionCacheLimit(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 1000, 100,100,100,100);
        updateExchangePositionDefaultLimit(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, 22845, 100);
    }

//    @BeforeAll
//    void changePositionLimit(){
//        adminSteps.updateExchangePosition(instrument.tickerSBER, instrument.tradingClearingAccountSBER, Exchange.MOEX,
//            true, 111, orderQuantityList(52, "default"), true);
//        adminSteps.updateExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.MOEX,
//            true, 21455, orderQuantityList(100, "default"), true);
//        adminSteps.updateExchangePosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, Exchange.SPB,
//            true, 11300, orderQuantityList(100, "default"), true);
//        updateexchangePositionCacheLimit(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, 1000, 10,10,10,10);
//    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategyService.findStrategyByContractId(contractIdMaster).get());
            } catch (Exception e) {
            }
            try {
                contractService.deleteContractById(contractIdMaster);
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
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyIdMaxCount);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignal(strategyId, versionNew);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyIdMaxCount);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                strategyTailValueDao.deleteStrategyTailValueByStrategyId(strategyIdMaxCount);
            } catch (Exception e) {
            }
            try {
                strategyTailValueDao.deleteStrategyTailValueByStrategyId(strategyId);
            } catch (Exception e) {
            }

        });
    }

    String description = "new test стратегия autotest";

    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("653779")
    @DisplayName("C653779.CreateSignal.Создания торгового сигнала ведущим, action = buy, позиция не найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C653779() {
        //было 1500
        double money = 7500.0;
        BigDecimal price = new BigDecimal("107.0");
        //Возможно тут была дробная позиция(до фикса было 2)
        BigDecimal quantityRequest = new BigDecimal("2.058");
        int version = 1;
        //mocksBasicSteps.createDataForMasterSignal(instrument.tickerAAPL, instrument.classCodeAAPL, "SPB", "MOEX",String.valueOf(price));
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerSBER, instrument.tradingClearingAccountSBER, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        double quantityReqBaseMoney = money - (price.multiply(quantityRequest)).doubleValue();
//        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        double quantityCommandBaseMoney = BigDecimal.valueOf(commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled(),
            commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale()).doubleValue();
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerSBER, instrument.tradingClearingAccountSBER, instrument.positionUIDSBER);
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("659115")
    @DisplayName("C659115.CreateSignal.Создания торгового сигнала ведущим, action = buy, позиция найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C659115() {
        double money = 3500.0;
        BigDecimal price = new BigDecimal("107.8");
        BigDecimal quantityRequest = new BigDecimal("0.01");
        int version = 2;
        double quantityPosMasterPortfolio = 12.0;
//        mocksBasicSteps.createDataForMasterSignal(instrument.tickerAAPL, instrument.classCodeAAPL, "SPB", "MOEX",String.valueOf(price));
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        double quantityReqBaseMoney = money - (price.multiply(quantityRequest)).doubleValue();
//        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        double quantityCommandBaseMoney = BigDecimal.valueOf(commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled(), commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale()).doubleValue();
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = quantityPosMasterPortfolio + quantityRequest.doubleValue();
//        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
//            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        double quantityPositionCommand = BigDecimal.valueOf(commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled(), commandKafka.getPortfolio().getPosition(0).getQuantity().getScale()).doubleValue();
        versionNew = version + 1;
        // проверяем значения в полученной команде
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionUIDAAPL);
    }




    @SneakyThrows
    @Test
    @Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("659236")
    @DisplayName("C659236.CreateSignal.Создания торгового сигнала ведущим, action =sell, позиция найдена в master_portfolio_position")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C659236() {
        double money = 3500.0;
        BigDecimal price = new BigDecimal("10.0");
        BigDecimal quantityRequest = new BigDecimal("4.0");
        int version = 3;
        double quantityPosMasterPortfolio = 12.0;
//        mocksBasicSteps.createDataForMasterSignal(instrument.tickerAAPL, instrument.classCodeAAPL, "SPB", "MOEX",String.valueOf(price));
        //создаем в БД tracking статегию на ведущего
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL, price, quantityRequest, strategyId,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        double quantityReqBaseMoney = money + (price.multiply(quantityRequest)).doubleValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = quantityPosMasterPortfolio - quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем параметры полученной команды
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 11, "SECURITY_SELL_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionUIDAAPL);
    }


    @SneakyThrows
    @Test
    //@Tags({@Tag("qa"), @Tag("qa2")})
    @AllureId("1312630")
    @DisplayName("C1312630.CreateSignal.Создание торгового сигнала для bond")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1312630() {
        double money = 1500.0;
        BigDecimal quantityRequest = new BigDecimal("3.0");
        int version = 1;
        //mocksBasicSteps.createDataForMasterSignal(instrument.tickerALFAperp, instrument.classCodeALFAperp, "SPB", "105");
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
//        getExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        BigDecimal price = new BigDecimal(steps.getPriceFromMarketData(instrument.instrumentALFAperp, "last"));
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        List<String> dateBond = steps.getPriceFromExchangePositionCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, SIEBEL_ID);
        String aciValue = dateBond.get(0);
        BigDecimal priceBond = price.add(new BigDecimal(aciValue));
        Instant createAt = Instant.ofEpochSecond(commandKafka.getCreatedAt().getSeconds(), commandKafka.getCreatedAt().getNanos());
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.ACTUALIZE));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdMaster));
        log.info("Команда в tracking.master.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMon = money - (priceBond.multiply(quantityRequest)).floatValue();
        BigDecimal quantityReqBaseMoney = new BigDecimal(Double.toString(quantityReqBaseMon));
        quantityReqBaseMoney = quantityReqBaseMoney.setScale(2, RoundingMode.HALF_UP);
        double quantityCommandBaseMon = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        BigDecimal quantityCommandBaseMoney = new BigDecimal(Double.toString(quantityCommandBaseMon));
        quantityCommandBaseMoney = quantityCommandBaseMoney.setScale(2, RoundingMode.HALF_UP);
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommandBond(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, priceBond,
            quantityRequest, instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, instrument.positionUIDALFAperp);
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
        BigDecimal quantityRequest = new BigDecimal("6.0");
        int version = 1;
        String tailValue = "6259.17";
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем запись в strategy_tail_value
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        BigDecimal signalValue = quantityRequest.multiply(price);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue, 4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue = new BigDecimal(tailValue).multiply(signalRate);
        //получаем стоимость позиции, из кэша exchangePositionPriceCache если action обрабатываемого сигнала = 'buy', то price_type = 'ask'
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "ask", SIEBEL_ID, instrument.instrumentAAPL);
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
        BigDecimal quantityRequest = new BigDecimal("3");
        int version = 2;
        String tailValue = "6259.17";
        double quantityPosMasterPortfolio = 6.0;
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель ведущего с позицией в кассандре
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL,instrument.positionIdAAPL, Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        //создаем запись в strategy_tail_value
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //создаем запись о прошлом сигнале в master_signal
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()),
            version, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107", "6", "25", 12);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
        //getExchangePosition(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, ExchangePosition.ExchangeEnum.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL, price, quantityRequest, strategyId,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //получаем стоимость позиции, которая есть у мастера из  кэша exchangePositionPriceCache
        String priceLast = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID, instrument.instrumentAAPL);
        //рассчитываем текущую общую стоимость портфеля portfolioValue без учета выставляемого сигнала:
        //сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(new BigDecimal(priceLast))
            .add(new BigDecimal(Double.toString(money)));
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = quantityRequest.multiply(price);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue, 4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue = new BigDecimal(tailValue).multiply(signalRate);
        //получаем стоимость позиции,  из  кэша exchangePositionPriceCache если action обрабатываемого сигнала = 'sell' price_type = 'bid';
        String priceBid = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "bid", SIEBEL_ID, instrument.instrumentAAPL);
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
        BigDecimal quantityRequest = new BigDecimal("1");
        int version = 2;
        String tailValue = "6259.17";
        double quantityPosMasterPortfolio = 3.0;
        BigDecimal minPriceIncrement = new BigDecimal("0.01");
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp,Double.toString(quantityPosMasterPortfolio));
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()), version,
            strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "103.05", "3", "1", 12);
        //проверяем бумагу по которой будем делать вызов CreateSignal, если бумаги нет создаем ее
//        getExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.SPB, true, 1000);
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        BigDecimal price = new BigDecimal(steps.getPriceFromMarketData(instrument.instrumentALFAperp, "last"));
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
        //рассчитываем текущую общую стоимость портфеля portfolioValue без учета выставляемого сигнала:
        String priceLast = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "last", SIEBEL_ID, instrument.instrumentAAPL);
        List<String> dateBond = steps.getPriceFromExchangePositionCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, SIEBEL_ID);
        String aciValue = dateBond.get(0);
        String nominal = dateBond.get(1);
        BigDecimal priceLastBond = steps.valuePosBonds(priceLast, nominal, minPriceIncrement, aciValue);
        // сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(priceLastBond)
            .add(new BigDecimal(Double.toString(money)));
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = quantityRequest.multiply(price);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue, 4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue = new BigDecimal(tailValue).multiply(signalRate);
        //получаем стоимость позиции, из кэша exchangePositionPriceCache если action обрабатываемого сигнала = 'buy', то price_type = 'ask'
        String priceBid = steps.getPriceFromExchangePositionPriceCache(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, "bid", SIEBEL_ID, instrument.instrumentAAPL);
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
    @AllureId("1740596")
    @DisplayName("C1740596.CreateSignal.Лимит сигналов за промежуток времени. " +
        "Создания торгового сигнала ведущим, action = Sell.Cтратегия в списке тяжеловесных. Количество сигналов < max-signals-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1740596() {
        double money = 784.84;
        BigDecimal price = new BigDecimal("107.0");
        BigDecimal quantityRequest = new BigDecimal("1.0");
        int version = 3;
        //создаем стратегию
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyIdMaxCount, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolioThreeVersion();
        //создаем записи по сигналам
        createMasterTwoSignal();
        //добавляем запись табл. strategy_tail_value
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyIdMaxCount, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL, price, quantityRequest, strategyIdMaxCount,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        log.info("Команда в tracking.master.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money + (price.multiply(quantityRequest)).floatValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 11, "SECURITY_SELL_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionUIDAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("1737220")
    @DisplayName("C1737220.CreateSignal.Лимит сигналов за промежуток времени. " +
        "Создания торгового сигнала ведущим, action = Buy.Cтратегия в списке тяжеловесных. Количество сигналов < max-signals-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1737220() {
        double money = 784.84;
        BigDecimal price = new BigDecimal("107.0");
        BigDecimal quantityRequest = new BigDecimal("3.0");
        int version = 3;
        //создаем стратегию
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyIdMaxCount, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolioThreeVersion();
        //создаем записи по сигналам
        createMasterTwoSignal();
        //добавляем запись табл. strategy_tail_value
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyIdMaxCount, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyIdMaxCount,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        log.info("Команда в tracking.master.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money - (price.multiply(quantityRequest)).floatValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition + 2, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionUIDAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("1740662")
    @DisplayName("C1740662.CreateSignal.Лимит сигналов за промежуток времени. " +
        "Создания торгового сигнала ведущим, action = Buy.Cтратегия в списке тяжеловесных. Количество сигналов = max-signals-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1740662() {
        double money = 891.84;
        BigDecimal price = new BigDecimal("107.0");
        BigDecimal quantityRequest = new BigDecimal("2.0");
        int version = 4;
        //создаем стратегию
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyIdMaxCount, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolioForVersionBuy();
        //создаем записи по сигналам
        createMasterThreeSignalBuy();
        //добавляем запись табл. strategy_tail_value
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyIdMaxCount, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyIdMaxCount,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        //проверяем параметры команды по синхронизации
        assertThat("Operation команды не равен", commandKafka.getOperation(), is(Tracking.PortfolioCommand.Operation.ACTUALIZE));
        assertThat("ContractId команды не равен", commandKafka.getContractId(), is(contractIdMaster));
        log.info("Команда в tracking.master.command:  {}", commandKafka);
        //считаем значение quantity по базовой валюте по формуле и приводитм полученное значение из команды к типу double
        double quantityReqBaseMoney = money - (price.multiply((quantityRequest)).floatValue());
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition + 1, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionUIDAAPL);
    }


    @SneakyThrows
    @Test
    @AllureId("1742644")
    @DisplayName("C1742644.CreateSignal.Лимит сигналов за промежуток времени. " +
        "Создания торгового сигнала ведущим, action = Sell.Cтратегия в списке тяжеловесных. Количество сигналов > max-signals-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1742644() {
        double money = 391.84;
        BigDecimal price = new BigDecimal("107.0");
        BigDecimal quantityRequest = new BigDecimal("1.0");
        int version = 5;
        //создаем стратегию
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyIdMaxCount, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolioFiveVersionSell();
        //создаем записи по сигналам
        createMasterForSignalSell();
        //добавляем запись табл. strategy_tail_value
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyIdMaxCount, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL, price, quantityRequest, strategyIdMaxCount,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
    }


    @SneakyThrows
    @Test
    @AllureId("1744047")
    @DisplayName("C1744047.CreateSignal.Лимит сигналов за промежуток времени. " +
        "Создания торгового сигнала ведущим, action = Buy.Cтратегии нет в списке тяжеловесных." +
        " Количество сигналов > max-signals-count.Сreated_at > now() - max-signals-seconds-time-span")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1744047() {
        double money = 391.84;
        BigDecimal price = new BigDecimal("107.0");
        BigDecimal quantityRequest = new BigDecimal("1.0");
        int version = 5;
        strategyId = UUID.randomUUID();
        //создаем стратегию
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позициями в кассандре
        createMasterPortfolioStrategyNotHeavyWeight();
        //создаем записи по сигналам
        createMasterSignalStrategyNotHeavyWeight();
        //добавляем запись табл. strategy_tail_value
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);
    }


    @SneakyThrows
    @Test
    @AllureId("1439791")
    @DisplayName("1439791 Объем заявки. Покупка. period = default. tailOrderQuantity < limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1439791() {
        BigDecimal price = new BigDecimal("105");
        BigDecimal quantityRequest = new BigDecimal("3.0");
        int version = 4;
        String tailValue = "150000";
        double money = 100000.0;
        double quantityPosMasterPortfolio = 0.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, null, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.SPB,
            true, 22845, orderQuantityList(100, "default"), false);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, version);
        // вызываем метод CreateSignal
        Response createSignal = signalApiCreator.get().createSignal()
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
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "ask", SIEBEL_ID, instrument.instrumentAAPL);
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = price.multiply(quantityRequest);
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
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerALFAperp));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountALFAperp));
}


    @SneakyThrows
    @Test
    @AllureId("1439796")
    @DisplayName("1439796 Объем заявки. Продажа. period = default. tailOrderQuantity < limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1439796() {
        BigDecimal price = new BigDecimal("105");
        BigDecimal quantityRequest = new BigDecimal("3.0");
        int version = 4;
        String tailValue = "150000";
        double money = 100000.0;
        double quantityPosMasterPortfolio = 0.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(2), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, "3");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.SPB,
            true, 22845, orderQuantityList(100, "default"), false);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, version);
        // вызываем метод CreateSignal
        Response createSignal = signalApiCreator.get().createSignal()
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
        String priceBid = steps.getPriceFromExchangePositionPriceCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "bid", SIEBEL_ID, instrument.instrumentALFAperp);
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(signalRate);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceBid), 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        //assertThat("price", masterSignal.getPrice(), is(price));
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerALFAperp));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountALFAperp));
    }



    @SneakyThrows
    @Test
    @AllureId("1440696")
    @DisplayName("1440696 Объем заявки. Продажа. period = additional_liquidity. limit не найден")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1440696() {
        BigDecimal price = new BigDecimal("3300.0");
        BigDecimal quantityRequest = new BigDecimal("3.0");
        int version = 4;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp,instrument.positionIdALFAperp,"3");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, "1000000", date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "3510000");
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.SPB,
            true, 22845, orderQuantityList(100, "additional_liquidity"), false);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
    }


    @SneakyThrows
    @Test
    @AllureId("1803799")
    @DisplayName("1803799 Объем заявки. Покупка. period = default. tailOrderQuantity < limit. dynamicsLimit = true")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1803799() {
        BigDecimal price = new BigDecimal("135");
        BigDecimal quantityRequest = new BigDecimal("10.0");
        int version = 4;
        String tailValue = "150000";
        double money = 100000.0;
        double quantityPosMasterPortfolio = 0.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, null, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(instrument.tickerSBER, instrument.tradingClearingAccountSBER, Exchange.SPB,
            true, 111, orderQuantityList(52, "default"), true);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(signalRate);
        //Получаем цену покупки
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(instrument.tickerSBER, instrument.tradingClearingAccountSBER, "ask", SIEBEL_ID, instrument.instrumentSBER);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceAsk), 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("tailOrderQuantity", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }


    @SneakyThrows
    @Test
    @AllureId("1799366")
    @DisplayName("1799366 Объем заявки. Продажа. period = default. tailOrderQuantity < limit. dynamicsLimit = true.")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1799366() {
        BigDecimal price = new BigDecimal("135");
        BigDecimal quantityRequest = new BigDecimal("10.0");
        int version = 4;
        String tailValue = "150000";
        double money = 100000.0;
        double quantityPosMasterPortfolio = 10.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithTestsContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.positionIdSBER, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(instrument.tickerSBER, instrument.tradingClearingAccountSBER, Exchange.SPB,
            true, 111, orderQuantityList(52, "default"), true);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(signalRate);
        //Получаем цену покупки
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(instrument.tickerSBER, instrument.tradingClearingAccountSBER, "ask", SIEBEL_ID, instrument.instrumentSBER);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceAsk), 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));


    }


    @SneakyThrows
    @Test
    @AllureId("1807504")
    @DisplayName("1807504 Объем заявки. Продажа. period = null. dynamicsLimit = false")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1807504() {
        BigDecimal price = new BigDecimal("135");
        BigDecimal quantityRequest = new BigDecimal("10.0");
        int version = 4;
        String tailValue = "150000";
        double money = 100000.0;
        double quantityPosMasterPortfolio = 0.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithTestsContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerSBER,
            instrument.tradingClearingAccountSBER, instrument.positionIdSBER,"20");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.createCommandUpdatePosition(instrument.tickerSBER, instrument.tradingClearingAccountSBER, 100);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER, version);
        // вызываем метод CreateSignal
        Response createSignal = signalApiCreator.get().createSignal()
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
        //Рассчитываем объем выставляемого сигнала
        BigDecimal signalValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal signalRate = signalValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(signalRate);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        //Получаем цену покупки
        String priceAsk = steps.getPriceFromExchangePositionPriceCache(instrument.tickerSBER, instrument.tradingClearingAccountSBER, "ask", SIEBEL_ID, instrument.instrumentSBER);
        BigDecimal tailOrderQuantity = tailOrderValue.divide(new BigDecimal(priceAsk), 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerSBER));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountSBER));
        assertThat("tailOrderQuantity", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }


    @SneakyThrows
    @Test
    @AllureId("1430348")
    @DisplayName("1430348 Лимит концентрации. action = buy. risk-profile = moderate. positionRate < max-position-rate")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1430348() {
       // mocksBasicSteps.createDataForMasterSignal(instrument.tickerALFAperp, instrument.classCodeALFAperp);
        BigDecimal price = new BigDecimal("105");
        BigDecimal quantityRequest = new BigDecimal("3.0");
        int version = 2;
        String tailValue = "150000";
        double money = 3885.0;
        double quantityPosMasterPortfolio = 3.0;
        double maxPositionRate = 0.25;
        BigDecimal minPriceIncrement = new BigDecimal("0.01");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.moderate,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(2), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerALFAperp,
            instrument.tradingClearingAccountALFAperp, instrument.positionIdALFAperp, "3");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //устанавливаем значения limit для проверяемого инструмента
        adminSteps.updateExchangePosition(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, Exchange.SPB,
            true, 22845, orderQuantityList(100, "default"), false);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
        //рассчёты
        //Считаем новую цену инструмента bond
        String priceLast = steps.getPriceFromExchangePositionPriceCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, "last", SIEBEL_ID, instrument.instrumentALFAperp);
        List<String> dateBond = steps.getPriceFromExchangePositionCache(instrument.tickerALFAperp, instrument.tradingClearingAccountALFAperp, SIEBEL_ID);
        String aciValue = dateBond.get(0);
        String nominal = dateBond.get(1);
        BigDecimal priceLastBond = steps.valuePosBonds(priceLast, nominal, minPriceIncrement, aciValue);
        // сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(priceLastBond)
            .add(new BigDecimal(Double.toString(money)));
        //Рассчитываем лимит концентрации
        BigDecimal positionValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal positionRate = positionValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(positionRate);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(priceLastBond, 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerALFAperp));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountALFAperp));
        assertThat("tailOrderQuantity", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }



    @SneakyThrows
    @Test
    @AllureId("1430357")
    @DisplayName("1430357 Лимит концентрации. action = buy. type = bond. risk-profile = aggressive. positionRate < max-position-rate")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1430357() {
        BigDecimal price = new BigDecimal("105");
        BigDecimal quantityRequest = new BigDecimal("2.0");
        int version = 2;
        String tailValue = "150000";
        double money = 345690;
        double quantityPosMasterPortfolio = 3.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(2), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "3");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY,
            price, quantityRequest, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
        //рассчёты
        //сумма всех позиции master_portoflio.positions умноженная на стоимость и плюс базовая валюта
        BigDecimal masterPortfolioValue = new BigDecimal(Double.toString(quantityPosMasterPortfolio))
            .multiply(price)
            .add(new BigDecimal(Double.toString(money)));
        //Рассчитываем лимит концентрации
        BigDecimal positionValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal positionRate = positionValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(positionRate);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(price, 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("tailOrderQuantity", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }



    @SneakyThrows
    @Test
    @AllureId("1430350")
    @DisplayName("1430350 Лимит концентрации. action = sell. Нет проверки")
    @Subfeature("Успешные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1430350() {
        BigDecimal price = new BigDecimal("105");
        BigDecimal quantityRequest = new BigDecimal("5.0");
        int version = 2;
        String tailValue = "1500";
        double money = 1000;
        double quantityPosMasterPortfolio = 10.0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        log.info("Получаем локальное время: {}", now);
        strategyId = UUID.randomUUID();
        //создаем в БД tracking стратегию на ведущего
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(2), 4, "0.2", "0.02",false, new BigDecimal(10),
            "WOW", "TestMan",true,true, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(date, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "10");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now().minusHours(5);
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), tailValue);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.SELL,
            price, quantityRequest, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, version);
        // вызываем метод CreateSignal
        signalApiCreator.get().createSignal()
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
        //Рассчитываем лимит концентрации
        BigDecimal positionValue = price.multiply(quantityRequest);
        //Рассчитываем долю сигнала относительно всего портфеля
        BigDecimal positionRate = positionValue.divide(masterPortfolioValue,4, RoundingMode.HALF_UP);
        //Определяем объем заявок на ведомых в случае выставления сигнала
        BigDecimal tailOrderValue =  new BigDecimal(tailValue).multiply(positionRate);
        //Рассчитываем количество единиц актива, которое будет выставлено для хвоста
        BigDecimal tailOrderQuantity = tailOrderValue.divide(price, 0, RoundingMode.HALF_UP);
        //Находим выставленный сигнал в БД
        await().atMost(FIVE_SECONDS).until(() ->
            masterSignal = masterSignalDao.getMasterSignalByVersion(strategyId, version + 1), notNullValue());
        //проверяем выставленный сигнал
        assertThat("quantity", masterSignal.getQuantity(), is(quantityRequest));
        assertThat("ticker", masterSignal.getTicker(), is(instrument.tickerAAPL));
        assertThat("tradingClearingAccount", masterSignal.getTradingClearingAccount(), is(instrument.tradingClearingAccountAAPL));
        assertThat("tailOrderQuantity", masterSignal.getTailOrderQuantity(), is(tailOrderQuantity));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa2")})
    @AllureId("1889573")
    @DisplayName("1889573 У позиции status IN (список из настройки trading-statuses со значением true), проверка на паузу если последняя приостановка торгов status NOT IN trading-statuses")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1889573() {
        double money = 1500.0;
        BigDecimal price = new BigDecimal("4.0");
        BigDecimal quantityRequest = new BigDecimal("3");
        int version = 1;
        strategyId = UUID.randomUUID();
//        mocksBasicSteps.createDataForMasterSignal(instrument.tickerYNDX, instrument.classCodeYNDX, "SPB", "MOEX",String.valueOf(price));
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //создаем и отправляем событие о статусе в топик test.topic.to.delete
        steps.createCommandForStatusCache(instrument.tickerYNDX, instrument.classCodeYNDX, "trading",  LocalDateTime.now().minusMinutes(31));
        steps.createCommandForStatusCache(instrument.tickerYNDX, instrument.classCodeYNDX, "normal_trading",  LocalDateTime.now().minusMinutes(25));
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, version);

        // вызываем метод CreateSignal
        Response createSignal = signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
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
        double quantityReqBaseMoney = money - (price.multiply(quantityRequest)).doubleValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerYNDX, instrument.tradingClearingAccountYNDX, instrument.positionUIDYNDX);
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa2")})
    @AllureId("1889522")
    @DisplayName("C1889522 У позиции status IN (список из настройки trading-statuses со значением true), не найдена последняя приостановка торгов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для создания торгового сигнала ведущим на увеличение/уменьшение соответствующей позиции в портфелях его ведомых.")
    void C1889522() {
        double money = 1500.0;
        BigDecimal price = new BigDecimal("4.0");
        BigDecimal quantityRequest = new BigDecimal("3");
        int version = 1;
        strategyId = UUID.randomUUID();
//        mocksBasicSteps.createDataForMasterSignal(instrument.tickerVTBM, instrument.classCodeVTBM, "SPB", "MOEX",String.valueOf(price));
        steps.createClientWithContractAndStrategy(SIEBEL_ID, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, version, Double.toString(money), date);
        OffsetDateTime cutTime = OffsetDateTime.now();
        steps.createDateStrategyTailValue(strategyId, Date.from(cutTime.toInstant()), "6259.17");
        //создаем и отправляем событие о статусе в топик test.topic.to.delete
        steps.createCommandForStatusCache(instrument.tickerVTBM, instrument.classCodeVTBM, "normal_trading",  LocalDateTime.now().minusMinutes(25));
        //вычитываем из топика кафка tracking.master.command
        steps.resetOffsetToLate(TRACKING_MASTER_COMMAND);
        //формируем тело запроса метода CreateSignal
        CreateSignalRequest request = createSignalRequest(CreateSignalRequest.ActionEnum.BUY, price, quantityRequest, strategyId,
            instrument.tickerVTBM, instrument.tradingClearingAccountVTBM, version);

        // вызываем метод CreateSignal
        Response createSignal = signalApiCreator.get().createSignal()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
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
        double quantityReqBaseMoney = money - (price.multiply(quantityRequest)).doubleValue();
        double quantityCommandBaseMoney = commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getBaseMoneyPosition().getQuantity().getScale());
        // считаем значение quantity по позиции в запросе по формуле и приводит полученное значение из команды к типу double
        double quantityPosition = 0.0 + quantityRequest.doubleValue();
        double quantityPositionCommand = commandKafka.getPortfolio().getPosition(0).getQuantity().getUnscaled()
            * Math.pow(10, -1 * commandKafka.getPortfolio().getPosition(0).getQuantity().getScale());
        // проверяем значения в полученной команде
        versionNew = version + 1;
        assertCommand(commandKafka, contractIdMaster, version, quantityPositionCommand, quantityCommandBaseMoney,
            quantityPosition, 12, "SECURITY_BUY_TRADE", quantityReqBaseMoney, price,
            quantityRequest, instrument.tickerVTBM, instrument.tradingClearingAccountVTBM, instrument.positionUIDBTBM);
    }


    //*** Методы для работы тестов ***
    //Метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
    void getExchangePosition(String ticker, String tradingClearingAccount, Exchange exchange,
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
            exchangePositionApiAdminCreator.get().createExchangePosition()
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
                       BigDecimal quantityRequest, String ticker, String tradingClearingAccount, UUID positionId) {
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
//        double pricePositionCommand = portfolioCommand.getSignal().getPrice().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getSignal().getPrice().getScale());
        double pricePositionCommand = BigDecimal.valueOf(portfolioCommand.getSignal().getPrice().getUnscaled(),
            portfolioCommand.getSignal().getPrice().getScale()).doubleValue();
        assertThat("значение price  не равен", BigDecimal.valueOf(pricePositionCommand), is(price));
//        double quaSignalPositionCommand = portfolioCommand.getSignal().getQuantity().getUnscaled()
//            * Math.pow(10, -1 * portfolioCommand.getSignal().getQuantity().getScale());
        double quaSignalPositionCommand = BigDecimal.valueOf(portfolioCommand.getSignal().getQuantity().getUnscaled(),
            portfolioCommand.getSignal().getQuantity().getScale()).doubleValue();
        assertThat("значение quantity  не равен", quaSignalPositionCommand, is(quantityRequest.doubleValue()));
        assertThat("значение signal.instrument_id не равен", byteStringToUUID(portfolioCommand.getSignal().getInstrumentId()), is(positionId));
    }


    void assertCommandBond(Tracking.PortfolioCommand portfolioCommand, String key, int version, double quantityPositionCommand,
                           BigDecimal quantityCommandBaseMoney, double quantityPosition, int actionValue, String action, BigDecimal quantityReqBaseMoney, BigDecimal price,
                           BigDecimal quantityRequest, String ticker, String tradingClearingAccount, UUID positionId) {
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
        assertThat("значение quantity  не равен", quaSignalPositionCommand, is(quantityRequest.doubleValue()));
        assertThat("значение signal.instrument_id не равен", byteStringToUUID(portfolioCommand.getSignal().getInstrumentId()), is(positionId));
    }


    public CreateSignalRequest createSignalRequest(CreateSignalRequest.ActionEnum actionEnum, BigDecimal price,
                                                   BigDecimal quantityRequest, UUID strategyId, String ticker,
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


    void createMasterPortfolioThreeVersion() {
        // создаем портфель ведущего с позициями в кассандре
        //первая версия портфеля с пустыми позицими
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, positionList, 1,
            "1500", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(7).toInstant()));
        //вторая версия портфеля с 1-й позицией
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(7).toInstant()), instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, positionMasterList, 2, "1284.84", Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusMinutes(5).toInstant()));
        //третья версия портфеля
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"2", instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, masterTwoPositions, 3, "784.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5).toInstant()));
    }

    void createMasterTwoSignal() {
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(7).toInstant()), 2, strategyIdMaxCount, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.58", "2", "8", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(5).toInstant()), 3, strategyIdMaxCount, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500", "1", "4", 12);
    }


    void createMasterPortfolioForVersionBuy() {
        //первая версия без позиций
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, positionList, 1, "1500", Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusMinutes(17).toInstant()));
        //вторая версия без с одной позицией
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(16).toInstant()), instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "2");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, positionMasterList, 2, "1284.84", Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusMinutes(16).toInstant()));
        //третья версия
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, "2", instrument.tickerFB, instrument.tradingClearingAccountFB,
            instrument.positionIdFB, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, masterTwoPositions, 3, "784.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant()));
        //четвертая версия
        List<MasterPortfolio.Position> masterTwoPositionsVersionFour = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(13).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL, "1", instrument.tickerFB, instrument.tradingClearingAccountFB,
            instrument.positionIdFB,  "1");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, masterTwoPositionsVersionFour, 4, "891.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(13).toInstant()));
    }


    void createMasterThreeSignalBuy() {
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(16).toInstant()), 2, strategyIdMaxCount, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.58", "2", "8", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(15).toInstant()), 3, strategyIdMaxCount, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500", "1", "4", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(13).toInstant()), 4, strategyIdMaxCount, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107", "1", "4", 11);
    }


    void createMasterPortfolioFiveVersionSell() {
        //первая версия без позиций
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, positionList, 1,
            "1500", Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(17).toInstant()));
        //вторая версия без с одной позицией
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(16).toInstant()), instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, positionMasterList, 2, "1284.84", Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusMinutes(16).toInstant()));
        //третья версия
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(15).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"2", instrument.tickerFB, instrument.tradingClearingAccountFB,instrument.positionIdFB, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, masterTwoPositions, 3, "784.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant()));
        //четвертая версия
        List<MasterPortfolio.Position> masterTwoPositionsVersionFour = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(14).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"1", instrument.tickerFB, instrument.tradingClearingAccountFB,instrument.positionIdFB, "1");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, masterTwoPositionsVersionFour, 4, "891.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(14).toInstant()));
        //пятая версия
        List<MasterPortfolio.Position> masterTwoPositionsVersionFive = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(13).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"1", instrument.tickerFB, instrument.tradingClearingAccountFB,instrument.positionIdFB, "2");
        steps.createMasterPortfolio(contractIdMaster, strategyIdMaxCount, masterTwoPositionsVersionFive, 5, "391.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(13).toInstant()));
    }


    void createMasterForSignalSell() {
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(17).toInstant()), 2, strategyIdMaxCount, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.58", "2", "8", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(16).toInstant()), 3, strategyIdMaxCount, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500", "1", "4", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(14).toInstant()), 4, strategyIdMaxCount, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107", "1", "4", 11);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(13).toInstant()), 5, strategyIdMaxCount, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500", "1", "4", 12);
    }

    void createMasterPortfolioStrategyNotHeavyWeight() {
        // создаем портфель ведущего с позициями в кассандре
        //первая версия без позиций
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionList, 1, "1500", Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusMinutes(17).toInstant()));
        //вторая версия без с одной позицией
        List<MasterPortfolio.Position> positionMasterList = steps.masterOnePositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(16).toInstant()), instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,"2");
        steps.createMasterPortfolio(contractIdMaster, strategyId, positionMasterList, 2, "1284.84", Date.from(OffsetDateTime
            .now(ZoneOffset.UTC).minusMinutes(16).toInstant()));
        //третья версия
        List<MasterPortfolio.Position> masterTwoPositions = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(5).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"2", instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB,"1");
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositions, 3, "784.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5).toInstant()));
        //четвертая версия
        List<MasterPortfolio.Position> masterTwoPositionsVersionFour = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(4).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"1", instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB,"1");
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositionsVersionFour, 4, "891.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(4).toInstant()));
        //пятая версия
        List<MasterPortfolio.Position> masterTwoPositionsVersionFive = steps.masterTwoPositions(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusDays(1).toInstant()), instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            instrument.positionIdAAPL,"1", instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB,"2");
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterTwoPositionsVersionFive, 5, "391.84",
            Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toInstant()));
    }


    void createMasterSignalStrategyNotHeavyWeight() {
        //создаем записи по сигналам
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(16).toInstant()), 2, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.58", "2", "8", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(5).toInstant()), 3, strategyId, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500", "1", "4", 12);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(4).toInstant()), 4, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107", "1", "4", 11);
        steps.createMasterSignalWithDateCreate(Date.from(OffsetDateTime
                .now(ZoneOffset.UTC).minusMinutes(1).toInstant()), 5, strategyId, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500", "1", "4", 12);
    }


    void updateExchangePositionCacheLimit (String ticker, String tradingClearingAccount, int dailyLimit, int additionalLiquidity,
                                       int defaultLimit, int mainTrading, int primary){
        //Обновляем данные по лимитам
        Tracking.ExchangePosition exchangePositionCommand = steps.updatePositionLimitsCommand(ticker, tradingClearingAccount, dailyLimit, additionalLiquidity, defaultLimit, mainTrading, primary);
        String keyCommand = "\n" +
            "\u0004" + ticker + "\u0012\n" + tradingClearingAccount;
        stringToByteSenderService.send(Topics.EXCHANGE_POSITION, keyCommand, exchangePositionCommand.toByteArray());
    }

    void updateExchangePositionDefaultLimit (String ticker, String tradingClearingAccount, int dailyLimit, int defaultLimit){
        //Обновляем данные по лимитам
        Tracking.ExchangePosition exchangePositionCommand = steps.updatePositionDefaultLimitCommand(ticker, tradingClearingAccount, dailyLimit, defaultLimit);
        String keyCommand = "\n" +
            "\u0004" + ticker + "\u0012\n" + tradingClearingAccount;
        stringToByteSenderService.send(Topics.EXCHANGE_POSITION, keyCommand, exchangePositionCommand.toByteArray());
    }

    void updateExchangePosition () {
        ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit orderQuantityLimit
            = new ru.qa.tinkoff.swagger.tracking_admin.model.OrderQuantityLimit();
        orderQuantityLimit.setLimit(52);
        orderQuantityLimit.setPeriodId("default");
        UpdateExchangePositionRequest updateExchangePosition = new UpdateExchangePositionRequest();
        updateExchangePosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        updateExchangePosition.exchange(Exchange.MOEX);
        updateExchangePosition.setOrderQuantityLimits(Collections.singletonList(orderQuantityLimit));
        updateExchangePosition.setTicker(instrument.tickerSBER);
        updateExchangePosition.setDailyQuantityLimit(111);
        updateExchangePosition.setTrackingAllowed(true);
        updateExchangePosition.setDynamicLimits(false);
        updateExchangePosition.setTradingClearingAccount(instrument.tradingClearingAccountSBER);
        exchangePositionApiAdminCreator.get().updateExchangePosition()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .body(updateExchangePosition);
    }

    public UUID byteStringToUUID (ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

}
