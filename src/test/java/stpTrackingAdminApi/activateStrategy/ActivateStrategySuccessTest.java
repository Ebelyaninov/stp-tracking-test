package stpTrackingAdminApi.activateStrategy;


import com.google.protobuf.ByteString;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Step;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.StrategyApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.tinkoff.invest.tracking.master.TrackingMaster;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("activateStrategy - Активация стратегии")
@Subfeature("Успешные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("activateStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})
public class ActivateStrategySuccessTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;

    Strategy strategy;
    String xApiKey = "x-api-key";
    String key = "tracking";
    String contractId;
    UUID investId;
    UUID strategyId;
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);
    String description = "Autotest  - ActivateStrategy";
    Integer score = 5;
    String siebelId;
    MasterPortfolioValue masterPortfolioValue;
    Contract contract;
    //Из настройки currency
    UUID positionIdRUB = UUID.fromString("33e24a92-aab0-409c-88b8-f2d57415b920");
    UUID positionIdUSD = UUID.fromString("6e97aa9b-50b6-4738-bce7-17313f2b2cc2");

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(steps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.client);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractId, strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioValueDao.deleteMasterPortfolioValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
        });
    }

    @BeforeAll
    void getDataClients() {
        siebelId = siebel.siebelIdAdmin;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdAdmin);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelId);
    }


    @Test
    @AllureId("457274")
    @DisplayName("C457274.ActivateStrategy. Успешная активация стратегии")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C457274() throws Exception {
        String title = steps.getTitleStrategy();
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null, null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        steps.createMasterPortfolio(contractId, strategyId, 1, "6551.10", masterPos);
       //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "6551.10", "6551.10");
        await().atMost(Duration.ofSeconds(12)).pollDelay(TEN_SECONDS).until(() ->
            strategyService.getStrategy(strategyId).getStatus().equals(StrategyStatus.draft));
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        steps.resetOffsetToLate(MASTER_PORTFOLIO_OPERATION);
        //Вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        OffsetDateTime now = OffsetDateTime.now();
        await().atMost(FIVE_SECONDS).pollDelay(TWO_HUNDRED_MILLISECONDS).until(() ->
            strategyService.getStrategy(strategyId).getStatus().equals(StrategyStatus.active));
        contract = contractService.getContract(contractId);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messagesFromTrackingStrategy = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> messageFromStrackingStrategy = messagesFromTrackingStrategy.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event eventFromTrackingStrategy = Tracking.Event.parseFrom(messageFromStrackingStrategy.getValue());
        //Проверяем, данные в сообщении
        checkStrategyEventParam(eventFromTrackingStrategy, "UPDATED", strategyId, title, contract.getClientId());
        List<Pair<String, byte[]>> messagesFromMasterPortfolioOperation = kafkaReceiver.receiveBatch(MASTER_PORTFOLIO_OPERATION, Duration.ofSeconds(20));
        Pair<String, byte[]> messageFromMasterPortfolioOperation = messagesFromMasterPortfolioOperation.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        TrackingMaster.MasterPortfolioOperation eventFromMasterPortfolioOperation = TrackingMaster.MasterPortfolioOperation.parseFrom(messageFromMasterPortfolioOperation.getValue());
        checkFirstAdjustEventParam(eventFromMasterPortfolioOperation,strategyId, now,1, positionIdRUB, new BigDecimal("6551.10"));
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, Currency.RUB, description, "active",
            StrategyRiskProfile.CONSERVATIVE, score);
    }


    @Test
    @AllureId("457351")
    @DisplayName("C457351.ActivateStrategy. Успешный ответ при повторной активации")
    @Description("Метод для администратора для перевода активации (публикации) стратегии.")
    void C457351() throws Exception {
        String title = steps.getTitleStrategy();
        strategyId = UUID.randomUUID();
        //Создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null, null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        steps.createMasterPortfolio(contractId, strategyId, 1, "6551.10", masterPos);
        steps.createMasterPortfolio(contractId, strategyId, 2, "18551.10", masterPos);
        //создаем запись в табл.master_portfolio_value  по стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 0, 4, "525.12", "6551.10");
        createDateMasterPortfolioValue(strategyId, 0, 2, "723.62", "6551.10");
        await().atMost(Duration.ofSeconds(12)).pollDelay(TEN_SECONDS).until(() ->
            strategyService.getStrategy(strategyId).getStatus().equals(StrategyStatus.draft));
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_STRATEGY_EVENT);
        steps.resetOffsetToLate(MASTER_PORTFOLIO_OPERATION);
        //Вызываем метод activateStrategy
        Response responseActiveStrategy = strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        OffsetDateTime now = OffsetDateTime.now();
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseActiveStrategy.getHeaders().getValue("x-server-time").isEmpty());
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_STRATEGY_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        contract = contractService.getContract(contractId);
        //Проверяем, данные в сообщении
        checkStrategyEventParam(event, "UPDATED", strategyId, title, contract.getClientId());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, Currency.USD, description, "active",
            StrategyRiskProfile.CONSERVATIVE, score);
        //Проверяем событие из топика tracking.master.portfolio.operation
        List<Pair<String, byte[]>> messagesFromMasterPortfolioOperation = kafkaReceiver.receiveBatch(MASTER_PORTFOLIO_OPERATION, Duration.ofSeconds(20));
        Pair<String, byte[]> messageFromMasterPortfolioOperation = messagesFromMasterPortfolioOperation.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        TrackingMaster.MasterPortfolioOperation eventFromMasterPortfolioOperation = TrackingMaster.MasterPortfolioOperation.parseFrom(messageFromMasterPortfolioOperation.getValue());
        checkFirstAdjustEventParam(eventFromMasterPortfolioOperation,strategyId, now,1, positionIdUSD, new BigDecimal("6551.10"));
        strategyApiStrategyApiAdminCreator.get().activateStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.asString());
        //Находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        checkStrategyParam(strategyId, contractId, title, Currency.USD, description, "active",
            StrategyRiskProfile.CONSERVATIVE, score);
    }


    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

    //Проверяем параметры события
    void checkStrategyEventParam(Tracking.Event event, String action, UUID strategyId, String title, UUID clientId) {
        assertAll(
            () -> assertThat("Action события не равен", event.getAction().toString(), is(action)),
            () -> assertThat("ID договора не равен", uuid(event.getStrategy().getId()), is(strategyId)),
            () -> assertThat("title стратегии не равен", (event.getStrategy().getTitle()), is(title)),
            () -> assertThat("strategy.status записи после обновления != ", event.getStrategy().getStatus().toString(), is(Tracking.Strategy.Status.ACTIVE.toString())),
            () -> assertThat("strategy.owner.invest_id записи после обновления != ", uuid(event.getStrategy().getOwner().getInvestId()), is(clientId))
        );
    }

    void checkFirstAdjustEventParam(TrackingMaster.MasterPortfolioOperation masterPortfolioOperation, UUID strategyId, OffsetDateTime changedAt, int firstMasterPortfolioVersion, UUID positionId, BigDecimal quantity) {
        BigDecimal adjustQuantity = BigDecimal.valueOf(masterPortfolioOperation.getAdjust().getQuantity().getUnscaled(),
            masterPortfolioOperation.getAdjust().getQuantity().getScale());
        Instant changedAtFromEventToInstance = Instant.ofEpochSecond(masterPortfolioOperation.getChangedAt().getSeconds(), masterPortfolioOperation.getChangedAt().getNanos());
        //Сравниваем, что полученая дата > now не больше чем на 6 с
        Long changedAtFromEvent = masterPortfolioOperation.getChangedAt().getSeconds() + masterPortfolioOperation.getChangedAt().getNanos();
        if (changedAtFromEvent.compareTo(changedAt.toEpochSecond()) >= 0 ){
            log.info("Получили дату больше чем changedAt из masterPortfolio");
        }
        assertAll(
            () -> assertThat("strategy_id не равен",  uuid(masterPortfolioOperation.getStrategyId()), is(strategyId)),
            () -> assertThat("portfolio.version не равен " + firstMasterPortfolioVersion, masterPortfolioOperation.getPortfolio().getVersion(), is(firstMasterPortfolioVersion)),
            () -> assertThat("adjust.position_id не равен " + positionId, uuid(masterPortfolioOperation.getAdjust().getPositionId()), is(positionId)),
            () -> assertThat("adjust.quantity базовой валюты != " + quantity, adjustQuantity, is(quantity)),
            () -> assertThat("Время changed_at не равно", changedAtFromEventToInstance.toString().substring(0, 18),
                is(changedAt.toInstant().toString().substring(0, 18)))
        );
    }

    //Проверяем параметры стратегии
    void checkStrategyParam(UUID strategyId, String contractId, String title, Currency baseCurrency,
                            String description, String status, StrategyRiskProfile riskProfile, Integer score) {
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", strategy.getTitle(), is(title));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("оценка стратегии не равно", strategy.getScore(), is(score));
        assertThat("валюта стратегии не равно", strategy.getBaseCurrency().toString(), is(baseCurrency.getValue()));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is(status));
        assertThat("риск-профиль стратегии не равно", strategy.getRiskProfile().toString(), is(riskProfile.toString()));
    }

    //дополнительные методы методы для работы тестов***************************************************
    @Step("Создаем запись в master_portfolio_value: ")
    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours,  String minimumValue, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .minimumValue(new BigDecimal(minimumValue))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }

    void confirmChangetAt(){

    }
}