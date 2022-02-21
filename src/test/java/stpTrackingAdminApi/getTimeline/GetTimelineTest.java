package stpTrackingAdminApi.getTimeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Step;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.*;
import ru.qa.tinkoff.investTracking.entities.ManagementFee;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.ResultFee;
import ru.qa.tinkoff.investTracking.entities.SlaveAdjust;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.model.CCYEV.CcyevEvent;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.TimelineApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.*;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.CCYEV;

@Slf4j
@Epic("getTimeline - Получение ленты событий")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("getTimeline")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    KafkaOldConfiguration.class
})
public class GetTimelineTest {
    TimelineApi timelineApi = ApiClient.api(ApiClient.Config.apiConfig()).timeline();
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    StpTrackingSlaveSteps slaveSteps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ManagementFeeDao managementFeeDao;
    @Autowired
    ResultFeeDao resultFeeDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlaveOrder2Dao slaveOrder2Dao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    SlaveAdjustDao slaveAdjustDao;
    @Autowired
    OldKafkaService oldKafkaService;

    String xApiKey = "x-api-key";

    //String siebelIdMaster = "1-51Q76AT";
    //String siebelIdSlave = "5-1P87U0B13";

    String siebelIdMaster = "5-F6VT91I0";
    String siebelIdSlave = "4-M3KKMT7";

    String contractIdMaster;
    String contractIdSlave;
    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;
    UUID idempotencyKey;

    Subscription subscription;
    SlavePortfolio slavePortfolio;
    MasterPortfolio masterPortfolio;
    SlaveOrder2 slaveOrder2;

    SlaveAdjust slaveAdjust;
    List<SlaveAdjust> slaveAdjustList;

    String operCode = "MNY_CHANGED_INP";
    String operId = "2321010121";

    String description = "new test стратегия autotest getTimeline";
    Integer score = 2;
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);


    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
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
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }

            try {
                managementFeeDao.deleteManagementFee(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                resultFeeDao.deleteResultFee(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("1672898")
    @DisplayName("C1672898.GetTimeline.Получаем сущности события о начислении комиссий в timeline: domain = management-fee")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1672898() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        //получаем данные по ведущему из пульса
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем записи в табл. management_fee
        createManagemetFee(subscriptionId);
        // преобразуем даты
        OffsetDateTime date = OffsetDateTime.now();
        Date dateAt = Date.from(date.toInstant());
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = steps.getimeline(request);
        // получаем записи из табл management_fee
        List<ManagementFee> managemenstFee = managementFeeDao.findListManagementFeeByCreateAt(contractIdSlave, strategyId, dateAt);
        //проверяем, данные в сообщении
        checkParamManagementFee(responseExep, managemenstFee);
    }


    @Test
    @AllureId("1674082")
    @DisplayName("C1674082.GetTimeline.Получаем сущности события о начислении комиссий в timeline: domain = management-fee, передан курсор")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1674082() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем записи в табл. management_fee
        createManagemetFee(subscriptionId);
        OffsetDateTime date = OffsetDateTime.now().minusDays(6);
        Date dateAt = Date.from(date.toInstant());
        String cursor = Long.toString(date.toInstant().toEpochMilli() * 1000);
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getStrategy
        GetTimelineResponse responseExep = timelineApi.getTimeline()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .body(request)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetTimelineResponse.class));
        List<ManagementFee> managementsFee = managementFeeDao.findListManagementFeeByCreateAt(contractIdSlave, strategyId, dateAt);
        //проверяем, данные в сообщении
        checkParamManagementFee(responseExep, managementsFee);
    }


    @Test
    @AllureId("1674102")
    @DisplayName("C1674102.GetTimeline.Получаем сущности события о начислении комиссий в timeline: domain = result-fee")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1674102() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(6);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        //создаем записи в табл. management_fee
        createFeeResult(startSubTime, subscriptionId);
        OffsetDateTime date = OffsetDateTime.now();
        Date dateAt = Date.from(date.toInstant());
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = steps.getimeline(request);
        //получаем записи из таблицы created_at_result_fee
        List<ResultFee> resultFees = resultFeeDao.findListResultFeeByCreateAt(contractIdSlave, strategyId, dateAt);
        //проверяем, данные в сообщении
        checkResultFeeParam(responseExep, resultFees);
    }


    @Test
    @AllureId("1674164")
    @DisplayName("C1674164.GetTimeline.Получаем сущности события о начислении комиссий в timeline: domain = result-fee, передан курсор")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1674164() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusMonths(3).minusDays(6);
        steps.createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, false, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
//        //получаем идентификатор подписки
        long subscriptionId = subscription.getId();
        OffsetDateTime date = OffsetDateTime.now().minusMonths(2);
        Date dateAt = Date.from(date.toInstant());
        String cursor = Long.toString(date.toInstant().toEpochMilli() * 1000);
        //создаем записи в табл. management_fee
        createFeeResult(startSubTime, subscriptionId);
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = timelineApi.getTimeline()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .cursorQuery(cursor)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetTimelineResponse.class));
        List<ResultFee> resultFees = resultFeeDao.findListResultFeeByCreateAt(contractIdSlave, strategyId, dateAt);
        //проверяем, данные в сообщении
        checkResultFeeParam(responseExep, resultFees);
    }


    @SneakyThrows
    @Test
    @AllureId("1586906")
    @DisplayName("1586906 Получаем сущности виртуального портфеля ведущего: domain = slave-portfolio")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1586906() {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем портфель master с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> masterPos = slaveSteps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "25", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем подписку на стратегию
        OffsetDateTime time = OffsetDateTime.now();
        slaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(time.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "13657.23";
        List<SlavePortfolio.Position> createListSlaveOnePos = slaveSteps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "1", date);
        slaveSteps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 2,
            baseMoneySl, date, createListSlaveOnePos);
        //отправляем команду на синхронизацию
        slaveSteps.createCommandSynTrackingSlaveCommand(contractIdSlave);
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = steps.getimeline(request);
        int index = responseExep.getItems().size() - 1;
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        steps.BlockContract(contractIdSlave);
        //проверяем данные в ответе
        assertThat("domain не равен", responseExep.getItems().get(index).getContent().getDomain().toString(), is("slave-portfolio"));
        assertThat("version не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getVersion(), is(slavePortfolio.getVersion()));
        assertThat("Master version не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getComparedToMasterVersion(), is(slavePortfolio.getComparedToMasterVersion()));
        //проверяем baseMoneyPosition
        assertThat("quantity не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getBaseMoneyPosition().getQuantity(), is(slavePortfolio.getBaseMoneyPosition().getQuantity()));
        assertThat("changed_at не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getBaseMoneyPosition().getLastChange().getChangedAt().toInstant(), is(slavePortfolio.getBaseMoneyPosition().getChangedAt().toInstant()));
        //проверяем positions
        assertThat("ticker не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getExchangePositionId().getTicker(), is(slavePortfolio.getPositions().get(0).getTicker()));
        assertThat("tradingClearingAccount не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getExchangePositionId().getTradingClearingAccount(), is(slavePortfolio.getPositions().get(0).getTradingClearingAccount()));
        assertThat("position quantity не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getQuantity(), is(slavePortfolio.getPositions().get(0).getQuantity()));
        assertThat("price не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getPrice().getValue().toString(), is(slavePortfolio.getPositions().get(0).getPrice().toString()));
        assertThat("price timestamp не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getPriceTimestamp().toInstant().truncatedTo(ChronoUnit.SECONDS), is(slavePortfolio.getPositions().get(0).getPrice_ts().toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("rate не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getRate().toString(), is(slavePortfolio.getPositions().get(0).getRate().toString()));
        assertThat("rateDiff не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getRateDiff().toString(), is(slavePortfolio.getPositions().get(0).getRateDiff().toString()));
        assertThat("quantityDiff не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getQuantityDiff().toString(), is(slavePortfolio.getPositions().get(0).getQuantityDiff().toString()));
        assertThat("buyEnabled не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getBuyEnabled(), is(slavePortfolio.getPositions().get(0).getBuyEnabled()));
        assertThat("sellEnabled не равен", ((SlavePortfolioItem) responseExep.getItems().get(index).getContent()).getPositions().get(0).getSellEnabled(), is(slavePortfolio.getPositions().get(0).getSellEnabled()));

    }


    @SneakyThrows
    @Test
    @AllureId("1586965")
    @DisplayName("1586965 Получаем сущности виртуального портфеля ведущего: domain = slave-order")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1586965() {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем подписку на стратегию
        OffsetDateTime time = OffsetDateTime.now();
        slaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(time.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 2, 1, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = steps.getimeline(request);
        //проверяем данные в ответе
        assertThat("domain не равен", responseExep.getItems().get(0).getContent().getDomain().toString(), is("slave-order"));
        assertThat("version не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getVersion(), is(slaveOrder2.getVersion()));
        assertThat("attempts count не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getAttemptsCount(), is(slaveOrder2.getAttemptsCount()));
        //проверяем positions
        String action;
        assertThat("ticker не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getExchangePositionId().getTicker(), is(slaveOrder2.getTicker()));
        assertThat("tradingClearingAccount не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getExchangePositionId().getTradingClearingAccount(), is(slaveOrder2.getTradingClearingAccount()));
        //if (((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getAction().toString().equals("buy"))
        if (slaveOrder2.getAction().intValue() == 0){
            action = "buy";
        }
        else {
            action = "sell";
        }
        assertThat("action не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getAction().toString(), is(action));
        assertThat("quantity не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getQuantity().intValue(), is(slaveOrder2.getQuantity().intValue()));
        assertThat("filled quantity не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getFilledQuantity().intValue(), is(slaveOrder2.getFilledQuantity().intValue()));
        assertThat("price не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getPrice().getValue().toString(), is(slaveOrder2.getPrice().toString()));
        assertThat("currency не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getPrice().getCurrency().toString(), is(strategyService.getStrategy(strategyId).getBaseCurrency().toString()));
        assertThat("state не равен", ((SlaveOrderItem) responseExep.getItems().get(0).getContent()).getState().intValue(), is(slaveOrder2.getState().intValue()));
    }


    private static Stream<Arguments> provideAction() {
        return Stream.of(
            Arguments.of("INSERT"),
            Arguments.of("UPDATE"),
            Arguments.of("DELETE")
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideAction")
    @AllureId("1664155")
    @DisplayName("1664155 Получаем сущности виртуального портфеля ведущего: domain = slave_adjust")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1664155(String action) {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем подписку на стратегию
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(3);
        slaveSteps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active,  new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),  null, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //отправляем событие о заводе
        LocalDateTime dateTime = LocalDateTime.now();
        String event = CcyevEvent.getKafkaTemplate(action, dateTime,
            "+12.79", "RUB", dateTime, operCode, operId, contractIdSlave, dateTime.minusMinutes(30));
        String key = contractIdSlave;
        oldKafkaService.send(CCYEV, key, event);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            slaveAdjustList = slaveAdjustDao.getSlaveAdjustByStrategyIdAndContract(contractIdSlave, strategyId), notNullValue());
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = steps.getimeline(request);
        //проверяем ответ метода
        assertThat("created_at не равен", responseExep.getItems().get(0).getTimestamp().toInstant().truncatedTo(ChronoUnit.SECONDS), is(slaveAdjustList.get(0).getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("domain не равен", responseExep.getItems().get(0).getContent().getDomain().toString(), is("slave-adjust"));
        assertThat("quantity не равен", ((SlaveAdjustItem) responseExep.getItems().get(0).getContent()).getQuantity().intValue(), is(slaveAdjustList.get(0).getQuantity().intValue()));
        assertThat("currency не равен", ((SlaveAdjustItem) responseExep.getItems().get(0).getContent()).getCurrency().toString(), is(slaveAdjustList.get(0).getCurrency()));
        assertThat("deleted не равен", ((SlaveAdjustItem) responseExep.getItems().get(0).getContent()).getDeleted(), is(slaveAdjustList.get(0).getDeleted()));
        assertThat("changed_at не равен", ((SlaveAdjustItem) responseExep.getItems().get(0).getContent()).getLastChange().getChangedAt().toInstant(), is(slaveAdjustList.get(0).getChangedAt().toInstant()));

    }



    @SneakyThrows
    @Test
    @AllureId("1586884")
    @DisplayName("1586884 Получаем сущности виртуального портфеля ведущего: domain = master-signal")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1586884() {
        strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(siebelIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, socialProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(30), score, expectedRelativeYield, "TEST", "OwnerTEST", true, true);
        //создаем портфель master с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> masterPos = slaveSteps.createListMasterPositionWithOnePos(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "25", date, 2, positionAction);
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "12259.17", masterPos);
        //создаем body post запроса
        GetTimelineRequest request = new GetTimelineRequest();
        request.setStrategyId(strategyId);
        request.setSlaveContractId(contractIdSlave);
        //вызываем метод getTimeline
        GetTimelineResponse responseExep = steps.getimeline(request);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster,strategyId);
        //проверяем ответ метода
        assertThat("domain не равен", responseExep.getItems().get(0).getContent().getDomain().toString(), is("master-portfolio"));
        assertThat("version не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getVersion().toString(), is(masterPortfolio.getVersion().toString()));
        assertThat("quantity базовой валюты не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getBaseMoneyPosition().getQuantity().toString(), is(masterPortfolio.getBaseMoneyPosition().getQuantity().toString()));
        assertThat("changed_at базовой валюты не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getBaseMoneyPosition().getLastChange().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("ticker инструмента не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getPositions().get(0).getExchangePositionId().getTicker(), is(masterPortfolio.getPositions().get(0).getTicker()));
        assertThat("TradingClearingAccount инструмента не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getPositions().get(0).getExchangePositionId().getTradingClearingAccount(), is(masterPortfolio.getPositions().get(0).getTradingClearingAccount()));
        assertThat("quantity инструмента не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getPositions().get(0).getQuantity().toString(), is(masterPortfolio.getPositions().get(0).getQuantity().toString()));
        assertThat("changed_at инструмента не равен", ((MasterPortfolioItem) responseExep.getItems().get(0).getContent()).getPositions().get(0).getLastChange().getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS), is(masterPortfolio.getPositions().get(0).getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS)));


    }

    //метод создает записи по заявкам в рамках одной стратегии
    @SneakyThrows
    void createTestDataSlaveOrder2 (int version, int count, int attemptsCounts, int action, String classCode, String ticker, String tradingClearingAccount) {
        idempotencyKey = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            attemptsCounts = attemptsCounts + 1;
            createSlaveOrder2(43, 9, contractIdSlave, strategyId, version, attemptsCounts, action, classCode,
                new BigDecimal("0"), idempotencyKey, new BigDecimal("173"), new BigDecimal("1"), (byte) 0, ticker, tradingClearingAccount);
            Thread.sleep(500);
        }
    }

    //метод для создания вставки заявки
    void createSlaveOrder2(int minusDays, int minusHours, String contractId, UUID strategyId, int version, Integer attemptsCount,
                           int action, String classCode, BigDecimal filledQuantity,
                           UUID idempotencyKey, BigDecimal price, BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {

        OffsetDateTime createAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(minusDays).minusHours(minusHours);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractId, createAt, strategyId, version, attemptsCount,
            action, classCode, 3, filledQuantity, idempotencyKey,
            UUID.randomUUID(), price, quantity, state,
            ticker, tradingClearingAccount);
    }

    void checkParamManagementFee(GetTimelineResponse responseExep, List<ManagementFee> managemenstFee) {
        assertThat("domain  не равен", responseExep.getItems().get(0).getContent().getDomain().toString(), is("management-fee"));
        assertThat("версия портфеля в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent()).getVersion()
            , is(managemenstFee.get(0).getVersion()));
        assertThat("settlementPeriod startedAt в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getSettlementPeriod().getStartedAt().toInstant(), is(managemenstFee.get(0).getSettlementPeriodStartedAt().toInstant()));
        assertThat("settlementPeriod endedAt в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getSettlementPeriod().getEndedAt().toInstant(), is(managemenstFee.get(0).getSettlementPeriodEndedAt().toInstant()));
        assertThat("PortfolioValue в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPortfolioValue(), is(managemenstFee.get(0).getContext().getPortfolioValue()));
        assertThat("Ticker Context  в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getExchangePositionId().getTicker(), is(managemenstFee.get(0).getContext().getPositions().get(0).getTicker()));
        assertThat("TradingClearingAccount Context  в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getExchangePositionId().getTradingClearingAccount(), is(managemenstFee.get(0).getContext().getPositions()
            .get(0).getTradingClearingAccount()));
        assertThat("Quantity Context  в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getQuantity(), is(managemenstFee.get(0).getContext().getPositions()
            .get(0).getQuantity()));
        assertThat("Price Context  в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getPrice().getValue(), is(managemenstFee.get(0).getContext().getPositions()
            .get(0).getPrice()));
        assertThat("Currency Context  в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getPrice().getCurrency().getValue(), is("rub"));
        assertThat("PriceTs Context  в managementFee  не равен", ((ManagementFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getPriceTimestamp().toInstant(), is(managemenstFee.get(0).getContext().getPositions()
            .get(0).getPriceTs().toInstant()));
    }


    void checkResultFeeParam(GetTimelineResponse responseExep, List<ResultFee> resultFees) {
        assertThat("domain  не равен", responseExep.getItems().get(0).getContent().getDomain().toString(), is("result-fee"));
        assertThat("версия портфеля в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent()).getVersion()
            , is(resultFees.get(0).getVersion()));
        assertThat("settlementPeriod startedAt в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getSettlementPeriod().getStartedAt().toInstant(), is(resultFees.get(0).getSettlementPeriodStartedAt().toInstant()));
        assertThat("settlementPeriod endedAt в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getSettlementPeriod().getEndedAt().toInstant(), is(resultFees.get(0).getSettlementPeriodEndedAt().toInstant()));
        assertThat("HighWaterMark endedAt в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getHighWaterMark(), is(resultFees.get(0).getHighWaterMark()));
        assertThat("PortfolioValue в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPortfolioValue(), is(resultFees.get(0).getContext().getPortfolioValue()));
        assertThat("Ticker Context  в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getExchangePositionId().getTicker(), is(resultFees.get(0).getContext().getPositions().get(0).getTicker()));
        assertThat("TradingClearingAccount Context  в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getExchangePositionId().getTradingClearingAccount(), is(resultFees.get(0).getContext().getPositions()
            .get(0).getTradingClearingAccount()));
        assertThat("Quantity Context  в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getQuantity(), is(resultFees.get(0).getContext().getPositions()
            .get(0).getQuantity()));
        assertThat("Price Context  в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getPrice().getValue(), is(resultFees.get(0).getContext().getPositions()
            .get(0).getPrice()));
        assertThat("Currency Context  в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getPrice().getCurrency().getValue(), is("rub"));
        assertThat("PriceTs Context  в resultFee  не равен", ((ResultFeeItem) responseExep.getItems().get(0).getContent())
            .getContext().getPositions().get(0).getPriceTimestamp().toInstant(), is(resultFees.get(0).getContext().getPositions()
            .get(0).getPriceTs().toInstant()));
    }

    @Step("Создаем записи по комиссии за результат в management_fee")
    void createManagemetFee(long subscriptionId) {
        //запись 1
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("25000.0"))
            .positions(positionListEmpty)
            .build();
        createManagementFee(contractIdSlave, strategyId, subscriptionId, 1,
            Date.from(LocalDate.now().minusDays(8).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(7).atStartOfDay().toInstant(UTC)), context,
            Date.from(LocalDate.now().minusDays(7).atStartOfDay().toInstant(UTC)));
        //запись 2
        List<Context.Positions> positionListWithPos = new ArrayList<>();
        positionListWithPos.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("258.45"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(6).toInstant()))
            .build());
        Context contextWithPos = Context.builder()
            .portfolioValue(new BigDecimal("23869.02"))
            .positions(positionListWithPos)
            .build();
        createManagementFee(contractIdSlave, strategyId, subscriptionId, 2,
            Date.from(LocalDate.now().minusDays(7).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(6).atStartOfDay().toInstant(UTC)), contextWithPos,
            Date.from(LocalDate.now().minusDays(7).atStartOfDay().toInstant(UTC)));
        //запись 3
        List<Context.Positions> positionListWithPos2 = new ArrayList<>();
        positionListWithPos2.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("258.45"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(6).toInstant()))
            .build());
        positionListWithPos2.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1072.24000"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(5).toInstant()))
            .build());
        Context contextWithPos2 = Context.builder()
            .portfolioValue(new BigDecimal("28701.24000"))
            .positions(positionListWithPos2)
            .build();
        createManagementFee(contractIdSlave, strategyId, subscriptionId, 3,
            Date.from(LocalDate.now().minusDays(6).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(5).atStartOfDay().toInstant(UTC)), contextWithPos2,
            Date.from(LocalDate.now().minusDays(5).atStartOfDay().toInstant(UTC)));

        //запись 4
        List<Context.Positions> positionListWithPos3 = new ArrayList<>();
        positionListWithPos3.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("258.45"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(6).toInstant()))
            .build());
        positionListWithPos3.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1072.24000"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(5).toInstant()))
            .build());
        positionListWithPos3.add(Context.Positions.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal("3"))
            .price(new BigDecimal("3413"))
            .priceTs(Date.from(OffsetDateTime.now().minusDays(5).toInstant()))
            .build());
        Context contextWithPos3 = Context.builder()
            .portfolioValue(new BigDecimal("38940.24000"))
            .positions(positionListWithPos3)
            .build();
        createManagementFee(contractIdSlave, strategyId, subscriptionId, 4,
            Date.from(LocalDate.now().minusDays(5).atStartOfDay().toInstant(UTC)),
            Date.from(LocalDate.now().minusDays(4).atStartOfDay().toInstant(UTC)), contextWithPos3,
            Date.from(LocalDate.now().minusDays(4).atStartOfDay().toInstant(UTC)));
    }


    public void createManagementFee(String contractIdSlave, UUID strategyId, long subscriptionId,
                                    int version, Date settlementPeriodStartedAt, Date settlementPeriodEndedAt,
                                    Context context, Date createdAt) {
        managementFeeDao.insertIntoManagementFee(contractIdSlave, strategyId, subscriptionId, version,
            settlementPeriodStartedAt, settlementPeriodEndedAt, context, createdAt);
    }

    @Step("Создаем записи по комиссии за результат в result_fee")
    void createFeeResult(OffsetDateTime startSubTime, long subscriptionId) {
        Date startFirst = Date.from(startSubTime.toLocalDate().atStartOfDay().toInstant(UTC));
        Date endFirst = Date.from(LocalDate.now().minusMonths(3).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmpty = new ArrayList<>();
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("297.73"))
            .priceTs(startFirst)
            .build());
        positionListEmpty.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1088.91000"))
            .priceTs(startFirst)
            .build());
        Context context = Context.builder()
            .portfolioValue(new BigDecimal("65162.50000"))
            .positions(positionListEmpty)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 1,
            startFirst, endFirst, context, new BigDecimal("65162.5"), endFirst);

        Date startSecond = Date.from(LocalDate.now().minusMonths(3).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        Date endSecond = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmptySecond = new ArrayList<>();
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("20"))
            .price(new BigDecimal("310.79"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1069.65000"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond.add(Context.Positions.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("4942"))
            .priceTs(startFirst)
            .build());
        Context contextSec = Context.builder()
            .portfolioValue(new BigDecimal("79880.40000"))
            .positions(positionListEmptySecond)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 2,
            startSecond, endSecond, contextSec, new BigDecimal("79880.4"), endSecond);

        Date startSecond2 = Date.from(LocalDate.now().minusMonths(2).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        Date endSecond2 = Date.from(LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(UTC));
        List<Context.Positions> positionListEmptySecond2 = new ArrayList<>();
        positionListEmptySecond2.add(Context.Positions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal("10"))
            .price(new BigDecimal("269.42"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond2.add(Context.Positions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal("5"))
            .price(new BigDecimal("1063.00000"))
            .priceTs(startFirst)
            .build());
        positionListEmptySecond2.add(Context.Positions.builder()
            .ticker(instrument.tickerYNDX)
            .tradingClearingAccount(instrument.tradingClearingAccountYNDX)
            .quantity(new BigDecimal("8"))
            .price(new BigDecimal("3717.6"))
            .priceTs(startFirst)
            .build());
        Context contextSec2 = Context.builder()
            .portfolioValue(new BigDecimal("69117.25000"))
            .positions(positionListEmptySecond2)
            .build();
        resultFeeDao.insertIntoResultFee(contractIdSlave, strategyId, subscriptionId, 3,
            startSecond2, endSecond2, contextSec2, new BigDecimal("79880.4"), endSecond2);
    }

}
